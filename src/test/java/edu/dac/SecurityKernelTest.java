package edu.dac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityKernelTest {
    private DacStore store;
    private SecurityKernel kernel;
    private Session admin;
    private Session alice;
    private Session bob;

    @BeforeEach
    void setUp() throws SQLException {
        store = new DacStore(":memory:");
        kernel = new SecurityKernel(store);
        admin = store.bootstrapAdmin("admin", "admin-pass-123".toCharArray());
        alice = store.register("alice", "alice-pass-123".toCharArray());
        bob = store.register("bob", "bob-pass-123".toCharArray());
    }

    @AfterEach
    void tearDown() throws SQLException {
        store.close();
    }

    @Test
    void ownerCanCreateReadWriteAndDelete() throws SQLException {
        long objectId = kernel.createObject(alice, "notes", "first");
        assertEquals("first", kernel.readObject(alice, objectId));
        kernel.writeObject(alice, objectId, "updated");
        assertEquals("updated", kernel.readObject(alice, objectId));
        kernel.deleteObject(alice, objectId);
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(alice, objectId));
    }

    @Test
    void aclGrantsAndRevokesIndividualRights() throws SQLException {
        long objectId = kernel.createObject(alice, "shared", "secret");
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(bob, objectId));
        kernel.grant(alice, objectId, "bob", EnumSet.of(Permission.READ));
        assertEquals("secret", kernel.readObject(bob, objectId));
        assertThrows(AccessDeniedException.class, () -> kernel.writeObject(bob, objectId, "changed"));
        kernel.grant(alice, objectId, "bob", EnumSet.of(Permission.WRITE));
        kernel.writeObject(bob, objectId, "changed");
        kernel.revoke(alice, objectId, "bob", EnumSet.of(Permission.READ));
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(bob, objectId));
    }

    @Test
    void adminCanAccessAnyObjectAndViewAcl() throws SQLException {
        long objectId = kernel.createObject(alice, "admin-check", "text");
        assertEquals("text", kernel.readObject(admin, objectId));
        assertTrue(kernel.aclMatrix(admin, objectId).contains(
                new DacStore.AclEntry("alice", Permission.READ)));
        kernel.deleteObject(admin, objectId);
        assertTrue(kernel.listObjects(alice).isEmpty());
    }

    @Test
    void deniedReadAndSafeTrojanAttemptAreAuditedWithoutReadingContent() throws SQLException {
        long objectId = kernel.createObject(alice, "protected", "do not disclose");
        assertEquals("DENIED", kernel.simulateTrojanRead(bob, objectId));
        List<DacStore.AuditEntry> entries = kernel.auditLog(admin, 100);
        DacStore.AuditEntry trojan = entries.stream()
                .filter(entry -> entry.operation().equals("TROJAN_READ_ATTEMPT"))
                .findFirst().orElseThrow();
        assertEquals("DENIED", trojan.result());
        assertFalse(trojan.details().contains("do not disclose"));
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(bob, objectId));
    }

    @Test
    void auditModesFilterOrdinaryOperationsButKeepSecurityEvents() throws SQLException {
        long objectId = kernel.createObject(alice, "audit-check", "value");
        kernel.setAuditMode(admin, AuditMode.SECURITY);
        int before = kernel.auditLog(admin, 1000).size();
        kernel.readObject(alice, objectId);
        assertEquals(before, kernel.auditLog(admin, 1000).size());
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(bob, objectId));
        assertTrue(kernel.auditLog(admin, 1000).stream().anyMatch(
                entry -> entry.operation().equals("READ") && entry.result().equals("DENIED")));
        kernel.setAuditMode(admin, AuditMode.OFF);
        int countOff = kernel.auditLog(admin, 1000).size();
        kernel.readObject(alice, objectId);
        assertEquals(countOff, kernel.auditLog(admin, 1000).size());
    }

    @Test
    void registrationLoginAndUniqueUsernameWork() throws SQLException {
        assertThrows(SQLException.class,
                () -> store.register("alice", "another-pass-123".toCharArray()));
        assertTrue(kernel.auditLog(admin, 100).stream().anyMatch(
                entry -> entry.operation().equals("REGISTER") && entry.result().equals("DENIED")));
        assertEquals(alice.userId(),
                store.authenticate("ALICE", "alice-pass-123".toCharArray()).userId());
        assertThrows(AuthenticationException.class,
                () -> store.authenticate("alice", "wrong-pass-123".toCharArray()));
    }

    @Test
    void nonAdminCannotChangeAuditModeOrReadAuditLog() {
        assertThrows(AccessDeniedException.class, () -> kernel.setAuditMode(alice, AuditMode.OFF));
        assertThrows(AccessDeniedException.class, () -> kernel.auditLog(alice, 100));
    }

    @Test
    void duplicateObjectNameDoesNotCreatePartialAcl() throws SQLException {
        kernel.createObject(alice, "same-name", "one");
        assertThrows(SQLException.class, () -> kernel.createObject(bob, "SAME-NAME", "two"));
        assertEquals(1, kernel.listObjects(alice).size());
        assertTrue(kernel.auditLog(admin, 100).stream().anyMatch(
                entry -> entry.operation().equals("CREATE")
                        && entry.target().equals("SAME-NAME")
                        && entry.result().equals("ERROR")));
    }

    @Test
    void adminBootstrapIsOneTimeOnly() {
        assertThrows(IllegalStateException.class,
                () -> store.bootstrapAdmin("secondadmin", "another-pass-123".toCharArray()));
    }

    @Test
    void sessionUsernameCannotBeForged() {
        Session forged = new Session(alice.userId(), "admin");
        assertThrows(AccessDeniedException.class, () -> kernel.listObjects(forged));
    }

    @Test
    void bellLaPadulaBlocksReadUpEvenWhenAclAllowsIt() throws SQLException {
        kernel.setClearance(admin, "alice", SecurityLevel.CONFIDENTIAL);
        long secret = kernel.createObject(bob, "classified-secret", "classified", SecurityLevel.SECRET);
        kernel.grant(admin, secret, "alice", EnumSet.of(Permission.READ));
        AccessDeniedException error = assertThrows(
                AccessDeniedException.class, () -> kernel.readObject(alice, secret));
        assertTrue(error.getMessage().contains("No Read Up"));
        assertEquals("DENIED", kernel.simulateTrojanRead(alice, secret));
    }

    @Test
    void bellLaPadulaBlocksWriteDownEvenWhenAclAllowsIt() throws SQLException {
        long unclassified = kernel.createObject(bob, "unclassified-record", "public");
        kernel.grant(admin, unclassified, "alice", EnumSet.of(Permission.WRITE));
        kernel.setClearance(admin, "alice", SecurityLevel.SECRET);
        AccessDeniedException error = assertThrows(
                AccessDeniedException.class, () -> kernel.writeObject(alice, unclassified, "leak"));
        assertTrue(error.getMessage().contains("No Write Down"));
        assertThrows(AccessDeniedException.class,
                () -> kernel.createObject(alice, "new-public-copy", "leak", SecurityLevel.UNCLASSIFIED));
        assertEquals("public", kernel.readObject(bob, unclassified));
    }

    @Test
    void securityPolicyAppliesToAdministratorsToo() throws SQLException {
        long secret = kernel.createObject(admin, "admin-secret", "classified", SecurityLevel.TOP_SECRET);
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(bob, secret));
        kernel.setClearance(admin, "admin", SecurityLevel.UNCLASSIFIED);
        assertThrows(AccessDeniedException.class, () -> kernel.readObject(admin, secret));
    }

    @Test
    void processMonitorRunsSafeTrojanSimulationThroughKernel() throws Exception {
        long objectId = kernel.createObject(alice, "monitored-secret", "never expose");
        try (ProcessMonitor monitor = new ProcessMonitor(kernel)) {
            monitor.submitTrojanSimulation(bob, objectId);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (monitor.events().get(0).state().equals("В очереди")
                    || monitor.events().get(0).state().equals("Работает")) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("Процесс не завершился за отведённое время.");
                }
                Thread.sleep(10);
            }
            ProcessMonitor.ProcessEvent event = monitor.events().get(0);
            assertEquals("Завершён", event.state());
            assertTrue(event.details().contains("DENIED"));
            assertFalse(event.details().contains("never expose"));
        }
    }

    @Test
    void demoSeedCreatesLabeledUsersAndObjects() throws SQLException {
        try (DacStore demoStore = new DacStore(":memory:")) {
            DemoData.seed(demoStore);
            SecurityKernel demoKernel = new SecurityKernel(demoStore);
            Session demoBob = demoStore.authenticate("bob", "BobDemo123!".toCharArray());
            Session demoAlice = demoStore.authenticate("alice", "AliceDemo123!".toCharArray());
            Session demoAdmin = demoStore.authenticate("admin", "AdminDemo123!".toCharArray());
            List<DacStore.ObjectInfo> objects = demoKernel.listObjects(demoBob);
            assertEquals(3, demoKernel.listObjects(demoAdmin).size());
            DacStore.ObjectInfo secret = objects.stream()
                    .filter(object -> object.classification() == SecurityLevel.SECRET)
                    .findFirst().orElseThrow();
            assertThrows(AccessDeniedException.class,
                    () -> demoKernel.readObject(demoBob, secret.id()));
            assertEquals(SecurityLevel.CONFIDENTIAL, demoKernel.clearance(demoAlice));
        }
    }

    @Test
    void existingDatabaseIsMigratedWithDefaultSecurityLabels() throws Exception {
        Path directory = Files.createTempDirectory("dac-migration-");
        Path database = directory.resolve("legacy.sqlite");
        try {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                            password_hash BLOB NOT NULL,
                            password_salt BLOB NOT NULL,
                            role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
                            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                statement.execute("""
                        CREATE TABLE objects (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                            content TEXT NOT NULL,
                            owner_id INTEGER NOT NULL REFERENCES users(id),
                            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                statement.execute("""
                        INSERT INTO users (username, password_hash, password_salt, role)
                        VALUES ('legacy', X'01', X'02', 'USER')
                        """);
                statement.execute("""
                        INSERT INTO objects (name, content, owner_id)
                        VALUES ('old-object', 'preserved', 1)
                        """);
            }
            try (DacStore migratedStore = new DacStore(database.toString())) {
                SecurityKernel migratedKernel = new SecurityKernel(migratedStore);
                Session legacy = new Session(1, "legacy");
                assertEquals(SecurityLevel.UNCLASSIFIED, migratedKernel.clearance(legacy));
                assertEquals(SecurityLevel.UNCLASSIFIED,
                        migratedStore.findObject(1).classification());
                assertEquals("preserved", migratedKernel.readObject(legacy, 1));
            }
        } finally {
            Files.deleteIfExists(database.resolveSibling(database.getFileName() + "-wal"));
            Files.deleteIfExists(database.resolveSibling(database.getFileName() + "-shm"));
            Files.deleteIfExists(database);
            Files.deleteIfExists(directory);
        }
    }
}
