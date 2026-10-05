package edu.dac;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DacStore implements AutoCloseable {
    private static final int PBKDF2_ROUNDS = 310_000;
    private static final int HASH_BYTES = 32;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,32}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Connection connection;

    public DacStore(String databasePath) throws SQLException {
        if (!":memory:".equals(databasePath)) {
            Path path = Path.of(databasePath);
            if (path.getParent() != null) {
                path.getParent().toFile().mkdirs();
            }
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        password_hash BLOB NOT NULL,
                        password_salt BLOB NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
                        clearance TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
                            CHECK (clearance IN ('UNCLASSIFIED', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET')),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS objects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        content TEXT NOT NULL,
                        owner_id INTEGER NOT NULL REFERENCES users(id),
                        classification TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
                            CHECK (classification IN ('UNCLASSIFIED', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET')),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS acl (
                        object_id INTEGER NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
                        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        permission TEXT NOT NULL CHECK (permission IN ('READ', 'WRITE', 'DELETE')),
                        PRIMARY KEY (object_id, user_id, permission)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        actor TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        target TEXT NOT NULL,
                        result TEXT NOT NULL,
                        details TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("INSERT OR IGNORE INTO settings (key, value) VALUES ('audit_mode', 'ALL')");
        }
        ensureColumn("users", "clearance", """
                ALTER TABLE users ADD COLUMN clearance TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
                    CHECK (clearance IN ('UNCLASSIFIED', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET'))
                """);
        ensureColumn("objects", "classification", """
                ALTER TABLE objects ADD COLUMN classification TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
                    CHECK (classification IN ('UNCLASSIFIED', 'CONFIDENTIAL', 'SECRET', 'TOP_SECRET'))
                """);
    }

    public synchronized Session register(String username, char[] password) throws SQLException {
        validateCredentials(username, password);
        connection.setAutoCommit(false);
        try {
            Session session = insertUser(username, password, Role.USER, SecurityLevel.UNCLASSIFIED);
            audit(username, "REGISTER", username, "SUCCESS", "Создана учётная запись.", true);
            connection.commit();
            return session;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 19
                    && exception.getMessage() != null
                    && exception.getMessage().contains("users.username")) {
                try {
                    audit(username, "REGISTER", username, "DENIED", "Имя пользователя занято.", true);
                    connection.commit();
                } catch (SQLException auditException) {
                    connection.rollback();
                    exception.addSuppressed(auditException);
                }
            } else {
                connection.rollback();
                throw exception;
            }
            throw exception;
        } catch (RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized Session bootstrapAdmin(String username, char[] password) throws SQLException {
        validateCredentials(username, password);
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM users WHERE role = 'ADMIN' LIMIT 1");
                 ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    audit(username, "BOOTSTRAP_ADMIN", username, "DENIED",
                            "Администратор уже создан.", true);
                    connection.commit();
                    throw new IllegalStateException("Администратор уже создан.");
                }
            }
            Session session = insertUser(username, password, Role.ADMIN, SecurityLevel.TOP_SECRET);
            audit(username, "BOOTSTRAP_ADMIN", username, "SUCCESS", "Создан администратор.", true);
            connection.commit();
            return session;
        } catch (SQLException | RuntimeException exception) {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized Session authenticate(String username, char[] password) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String actor = username != null && USERNAME_PATTERN.matcher(username).matches()
                    ? username : "invalid-input";
            if (username == null || !USERNAME_PATTERN.matcher(username).matches()
                    || password == null || password.length > 1024) {
                audit(actor, "LOGIN", "-", "DENIED", "Неверные учётные данные.", true);
                connection.commit();
                throw new AuthenticationException("Неверное имя пользователя или пароль.");
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, username, password_hash, password_salt FROM users WHERE username = ? COLLATE NOCASE")) {
                query.setString(1, username);
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next() || !verifyPassword(password, result.getBytes("password_salt"),
                            result.getBytes("password_hash"))) {
                        audit(actor, "LOGIN", "-", "DENIED", "Неверные учётные данные.", true);
                        connection.commit();
                        throw new AuthenticationException("Неверное имя пользователя или пароль.");
                    }
                    Session session = new Session(result.getLong("id"), result.getString("username"));
                    audit(session.username(), "LOGIN", "-", "SUCCESS", "Вход выполнен.", true);
                    connection.commit();
                    return session;
                }
            }
        } catch (SQLException | RuntimeException exception) {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    synchronized Role currentRole(long userId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT role FROM users WHERE id = ?")) {
            query.setLong(1, userId);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? Role.valueOf(result.getString(1)) : null;
            }
        }
    }

    synchronized String currentUsername(long userId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT username FROM users WHERE id = ?")) {
            query.setLong(1, userId);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    synchronized SecurityLevel currentClearance(long userId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT clearance FROM users WHERE id = ?")) {
            query.setLong(1, userId);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? SecurityLevel.valueOf(result.getString(1)) : null;
            }
        }
    }

    synchronized void updateClearance(long userId, SecurityLevel level) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE users SET clearance = ? WHERE id = ?")) {
            update.setString(1, level.name());
            update.setLong(2, userId);
            update.executeUpdate();
        }
    }

    synchronized List<UserInfo> listUsers() throws SQLException {
        List<UserInfo> users = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id, username, role, clearance FROM users ORDER BY username");
             ResultSet result = query.executeQuery()) {
            while (result.next()) {
                users.add(new UserInfo(result.getLong("id"), result.getString("username"),
                        Role.valueOf(result.getString("role")),
                        SecurityLevel.valueOf(result.getString("clearance"))));
            }
        }
        return users;
    }

    synchronized boolean hasNoUsers() throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM users LIMIT 1");
             ResultSet result = query.executeQuery()) {
            return !result.next();
        }
    }

    synchronized boolean hasAdministrator() throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM users WHERE role = 'ADMIN' LIMIT 1");
             ResultSet result = query.executeQuery()) {
            return result.next();
        }
    }

    synchronized UserObject findObject(long objectId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id, name, owner_id, classification FROM objects WHERE id = ?")) {
            query.setLong(1, objectId);
            try (ResultSet result = query.executeQuery()) {
                return result.next()
                        ? new UserObject(result.getLong("id"), result.getString("name"),
                                result.getLong("owner_id"),
                                SecurityLevel.valueOf(result.getString("classification")))
                        : null;
            }
        }
    }

    synchronized boolean hasPermission(long objectId, long userId, Permission permission) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM acl WHERE object_id = ? AND user_id = ? AND permission = ?")) {
            query.setLong(1, objectId);
            query.setLong(2, userId);
            query.setString(3, permission.name());
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    synchronized long insertObject(String name, String content, long ownerId,
                                   SecurityLevel classification) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO objects (name, content, owner_id, classification) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, content);
            insert.setLong(3, ownerId);
            insert.setString(4, classification.name());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Не удалось получить ID созданного объекта.");
    }

    synchronized void grantPermission(long objectId, long userId, Permission permission) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO acl (object_id, user_id, permission) VALUES (?, ?, ?)")) {
            insert.setLong(1, objectId);
            insert.setLong(2, userId);
            insert.setString(3, permission.name());
            insert.executeUpdate();
        }
    }

    synchronized void revokePermission(long objectId, long userId, Permission permission) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM acl WHERE object_id = ? AND user_id = ? AND permission = ?")) {
            delete.setLong(1, objectId);
            delete.setLong(2, userId);
            delete.setString(3, permission.name());
            delete.executeUpdate();
        }
    }

    synchronized Long findUserId(String username) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getLong(1) : null;
            }
        }
    }

    synchronized String readContent(long objectId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT content FROM objects WHERE id = ?")) {
            query.setLong(1, objectId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Объект больше не существует.");
                }
                return result.getString(1);
            }
        }
    }

    synchronized void writeContent(long objectId, String content) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE objects SET content = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            update.setString(1, content);
            update.setLong(2, objectId);
            update.executeUpdate();
        }
    }

    synchronized void deleteObject(long objectId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM objects WHERE id = ?")) {
            delete.setLong(1, objectId);
            delete.executeUpdate();
        }
    }

    synchronized List<ObjectInfo> listVisibleObjects(long userId, boolean administrator) throws SQLException {
        List<ObjectInfo> objects = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT DISTINCT o.id, o.name, u.username AS owner, o.classification
                FROM objects o JOIN users u ON u.id = o.owner_id
                LEFT JOIN acl a ON a.object_id = o.id AND a.user_id = ?
                WHERE ? = 1 OR o.owner_id = ? OR a.permission IS NOT NULL
                ORDER BY o.id
                """)) {
            query.setLong(1, userId);
            query.setInt(2, administrator ? 1 : 0);
            query.setLong(3, userId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    objects.add(new ObjectInfo(result.getLong("id"), result.getString("name"),
                            result.getString("owner"),
                            SecurityLevel.valueOf(result.getString("classification"))));
                }
            }
        }
        return objects;
    }

    synchronized List<AclEntry> aclForObject(long objectId) throws SQLException {
        List<AclEntry> entries = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT u.username, a.permission
                FROM acl a JOIN users u ON u.id = a.user_id
                WHERE a.object_id = ? ORDER BY u.username, a.permission
                """)) {
            query.setLong(1, objectId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    entries.add(new AclEntry(result.getString("username"),
                            Permission.valueOf(result.getString("permission"))));
                }
            }
        }
        return entries;
    }

    synchronized AuditMode auditMode() throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT value FROM settings WHERE key = 'audit_mode'");
             ResultSet result = query.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Не задан режим аудита.");
            }
            return AuditMode.valueOf(result.getString(1));
        }
    }

    synchronized void updateAuditMode(AuditMode mode) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE settings SET value = ? WHERE key = 'audit_mode'")) {
            update.setString(1, mode.name());
            update.executeUpdate();
        }
    }

    synchronized void audit(String actor, String operation, String target, String result,
                            String details, boolean securityEvent) throws SQLException {
        auditWithMode(actor, operation, target, result, details, securityEvent, auditMode());
    }

    synchronized void auditWithMode(String actor, String operation, String target, String result,
                                   String details, boolean securityEvent, AuditMode mode)
            throws SQLException {
        if (mode == AuditMode.OFF || (mode == AuditMode.SECURITY && !securityEvent)) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO audit_log (actor, operation, target, result, details)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, actor);
            insert.setString(2, operation);
            insert.setString(3, target);
            insert.setString(4, result);
            insert.setString(5, details);
            insert.executeUpdate();
        }
    }

    synchronized List<AuditEntry> readAuditLog(int limit) throws SQLException {
        List<AuditEntry> entries = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT id, occurred_at, actor, operation, target, result, details
                FROM audit_log ORDER BY id DESC LIMIT ?
                """)) {
            query.setInt(1, Math.max(1, Math.min(limit, 1000)));
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    entries.add(new AuditEntry(result.getLong("id"), result.getString("occurred_at"),
                            result.getString("actor"), result.getString("operation"),
                            result.getString("target"), result.getString("result"),
                            result.getString("details")));
                }
            }
        }
        return entries;
    }

    synchronized void begin() throws SQLException {
        connection.setAutoCommit(false);
    }

    synchronized void commit() throws SQLException {
        connection.commit();
    }

    synchronized void rollback() throws SQLException {
        connection.rollback();
    }

    synchronized boolean inTransaction() throws SQLException {
        return !connection.getAutoCommit();
    }

    synchronized void endTransaction() throws SQLException {
        connection.setAutoCommit(true);
    }

    private Session insertUser(String username, char[] password, Role role,
                               SecurityLevel clearance) throws SQLException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO users (username, password_hash, password_salt, role, clearance) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, username);
            insert.setBytes(2, hashPassword(password, salt));
            insert.setBytes(3, salt);
            insert.setString(4, role.name());
            insert.setString(5, clearance.name());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Session(keys.getLong(1), username);
                }
            }
        }
        throw new SQLException("Не удалось создать пользователя.");
    }

    private static void validateCredentials(String username, char[] password) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Имя: 3–32 символа (латиница, цифры, _).");
        }
        if (password == null || password.length < 8 || password.length > 1024) {
            throw new IllegalArgumentException("Пароль должен содержать от 8 до 1024 символов.");
        }
    }

    private static byte[] hashPassword(char[] password, byte[] salt) {
        return deriveKey(password, salt);
    }

    private static boolean verifyPassword(char[] password, byte[] salt, byte[] expected) {
        return password != null && MessageDigest.isEqual(deriveKey(password, salt), expected);
    }

    private static byte[] deriveKey(char[] password, byte[] salt) {
        char[] chars = password == null ? new char[0] : password;
        javax.crypto.spec.PBEKeySpec keySpec = new javax.crypto.spec.PBEKeySpec(
                chars, salt, PBKDF2_ROUNDS, HASH_BYTES * 8);
        try {
            return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 недоступен в этой Java Runtime.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    private void ensureColumn(String table, String column, String alterStatement) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(alterStatement);
        }
    }

    record UserObject(long id, String name, long ownerId, SecurityLevel classification) {
    }

    public record ObjectInfo(long id, String name, String owner, SecurityLevel classification) {
    }

    public record UserInfo(long id, String username, Role role, SecurityLevel clearance) {
    }

    public record AclEntry(String username, Permission permission) {
    }

    public record AuditEntry(long id, String occurredAt, String actor, String operation,
                             String target, String result, String details) {
    }

}
