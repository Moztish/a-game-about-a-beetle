package edu.dac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
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
        kernel.writeObject(admin, objectId, "admin-updated");
        assertEquals("admin-updated", kernel.readObject(alice, objectId));
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
}
