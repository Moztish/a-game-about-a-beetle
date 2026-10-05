package edu.dac;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SecurityKernel {
    private final DacStore store;

    public SecurityKernel(DacStore store) {
        this.store = store;
    }

    public synchronized long createObject(Session session, String name, String content) throws SQLException {
        if (name == null || name.isBlank() || name.trim().length() > 80) {
            throw new IllegalArgumentException("Имя объекта должно содержать от 1 до 80 символов.");
        }
        validateContent(content);
        return run(session, "CREATE", name.trim(), null, null, false, () -> {
            long objectId = store.insertObject(name.trim(), content, session.userId());
            for (Permission permission : Permission.values()) {
                store.grantPermission(objectId, session.userId(), permission);
            }
            return objectId;
        });
    }

    public synchronized String readObject(Session session, long objectId) throws SQLException {
        return run(session, "READ", Long.toString(objectId), objectId, Permission.READ, false,
                () -> store.readContent(objectId));
    }

    public synchronized void writeObject(Session session, long objectId, String content) throws SQLException {
        validateContent(content);
        run(session, "WRITE", Long.toString(objectId), objectId, Permission.WRITE, false, () -> {
            store.writeContent(objectId, content);
            return null;
        });
    }

    public synchronized void deleteObject(Session session, long objectId) throws SQLException {
        run(session, "DELETE", Long.toString(objectId), objectId, Permission.DELETE, false, () -> {
            store.deleteObject(objectId);
            return null;
        });
    }

    public synchronized void grant(Session session, long objectId, String username,
                                   Set<Permission> permissions) throws SQLException {
        validatePermissions(permissions);
        run(session, "GRANT", Long.toString(objectId), objectId, null, true, () -> {
            Long userId = store.findUserId(username);
            if (userId == null) {
                throw new IllegalArgumentException("Пользователь не найден.");
            }
            if (userId == store.findObject(objectId).ownerId()) {
                throw new IllegalArgumentException("Права владельца нельзя изменить через ACL.");
            }
            for (Permission permission : permissions) {
                store.grantPermission(objectId, userId, permission);
            }
            return null;
        });
    }

    public synchronized void revoke(Session session, long objectId, String username,
                                    Set<Permission> permissions) throws SQLException {
        validatePermissions(permissions);
        run(session, "REVOKE", Long.toString(objectId), objectId, null, true, () -> {
            Long userId = store.findUserId(username);
            if (userId == null) {
                throw new IllegalArgumentException("Пользователь не найден.");
            }
            if (userId == store.findObject(objectId).ownerId()) {
                throw new IllegalArgumentException("Права владельца нельзя изменить через ACL.");
            }
            for (Permission permission : permissions) {
                store.revokePermission(objectId, userId, permission);
            }
            return null;
        });
    }

    public synchronized List<DacStore.ObjectInfo> listObjects(Session session) throws SQLException {
        return run(session, "LIST_OBJECTS", "-", null, null, false,
                () -> store.listVisibleObjects(session.userId()));
    }

    public synchronized List<DacStore.AclEntry> aclMatrix(Session session, long objectId) throws SQLException {
        return run(session, "VIEW_ACL", Long.toString(objectId), objectId, null, false, () -> {
            DacStore.UserObject object = store.findObject(objectId);
            if (store.currentRole(session.userId()) != Role.ADMIN
                    && object.ownerId() != session.userId()) {
                deny(session, "VIEW_ACL", object.name(),
                        "Матрицу ACL видит владелец или администратор.");
            }
            return store.aclForObject(objectId);
        });
    }

    public synchronized void setAuditMode(Session session, AuditMode mode) throws SQLException {
        if (mode == null) {
            throw new IllegalArgumentException("Режим аудита не задан.");
        }
        run(session, "SET_AUDIT_MODE", "-", null, null, false, () -> {
            if (store.currentRole(session.userId()) != Role.ADMIN) {
                deny(session, "SET_AUDIT_MODE", "-",
                        "Менять режим аудита может только администратор.");
            }
            AuditMode previousMode = store.auditMode();
            store.updateAuditMode(mode);
            store.auditWithMode(session.username(), "SET_AUDIT_MODE", "-", "SUCCESS",
                    "Режим аудита изменён на " + mode + ".", true, previousMode);
            return null;
        });
    }

    public synchronized List<DacStore.AuditEntry> auditLog(Session session, int limit) throws SQLException {
        return run(session, "VIEW_AUDIT", "-", null, null, false, () -> {
            if (store.currentRole(session.userId()) != Role.ADMIN) {
                deny(session, "VIEW_AUDIT", "-", "Журнал аудита доступен только администратору.");
            }
            return store.readAuditLog(limit);
        });
    }

    public synchronized String simulateTrojanRead(Session session, long objectId) throws SQLException {
        return run(session, "TROJAN_READ_ATTEMPT", Long.toString(objectId), objectId,
                null, false, () -> {
                    DacStore.UserObject object = store.findObject(objectId);
                    boolean allowed = store.currentRole(session.userId()) == Role.ADMIN
                            || object.ownerId() == session.userId()
                            || store.hasPermission(objectId, session.userId(), Permission.READ);
                    return allowed ? "WOULD_ALLOW" : "DENIED";
                });
    }

    private <T> T run(Session session, String operation, String target, Long objectId,
                      Permission permission, boolean ownerOnly, SqlAction<T> action) throws SQLException {
        synchronized (store) {
            store.begin();
            try {
                Role role = store.currentRole(session.userId());
                String username = store.currentUsername(session.userId());
                if (role == null || !Objects.equals(session.username(), username)) {
                    store.audit(username == null ? "invalid-session" : username, operation, target,
                            "DENIED", "Учётная запись не найдена или сессия недействительна.", true);
                    store.commit();
                    throw new AccessDeniedException("Учётная запись не найдена или сессия недействительна.");
                }
                DacStore.UserObject object = null;
                if (objectId != null) {
                    object = store.findObject(objectId);
                    if (object == null) {
                        deny(session, operation, target, "Объект не найден.");
                    }
                    boolean isOwner = object.ownerId() == session.userId();
                    if (ownerOnly && !isOwner && role != Role.ADMIN) {
                        deny(session, operation, object.name(), "Требуются права владельца.");
                    }
                    if (permission != null && !isOwner && role != Role.ADMIN
                            && !store.hasPermission(objectId, session.userId(), permission)) {
                        deny(session, operation, object.name(), "Нет права " + permission + ".");
                    }
                }
                T result = action.execute();
                String auditTarget = object == null ? target : object.name();
                boolean trojanAttempt = operation.equals("TROJAN_READ_ATTEMPT");
                boolean auditModeChange = operation.equals("SET_AUDIT_MODE");
                String auditResult = trojanAttempt ? result.toString() : "SUCCESS";
                String auditDetails = trojanAttempt
                        ? "Безопасная симуляция: проверены только права, содержимое не читалось."
                        : "Операция выполнена.";
                if (!auditModeChange) {
                    store.audit(username, operation, auditTarget, auditResult,
                            auditDetails, isSecurityEvent(operation));
                }
                store.commit();
                return result;
            } catch (AccessDeniedException exception) {
                if (store.inTransaction()) {
                    store.rollback();
                }
                throw exception;
            } catch (SQLException | RuntimeException exception) {
                if (store.inTransaction()) {
                    try {
                        store.audit(session.username(), operation, target, "ERROR",
                                "Операция завершилась ошибкой: " + exception.getClass().getSimpleName() + ".",
                                isSecurityEvent(operation));
                        store.commit();
                    } catch (SQLException auditException) {
                        store.rollback();
                        exception.addSuppressed(auditException);
                    }
                }
                throw exception;
            } finally {
                store.endTransaction();
            }
        }
    }

    private void deny(Session session, String operation, String target, String details) throws SQLException {
        store.audit(session.username(), operation, target, "DENIED", details, true);
        store.commit();
        throw new AccessDeniedException(details);
    }

    private static boolean isSecurityEvent(String operation) {
        return Set.of("DENIED", "REGISTER", "LOGIN", "BOOTSTRAP_ADMIN", "SET_AUDIT_MODE",
                "GRANT", "REVOKE", "TROJAN_READ_ATTEMPT").contains(operation);
    }

    private static void validateContent(String content) {
        if (content == null || content.length() > 100_000) {
            throw new IllegalArgumentException("Содержимое должно содержать от 0 до 100000 символов.");
        }
    }

    private static void validatePermissions(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Нужно указать хотя бы одно право.");
        }
    }

    @FunctionalInterface
    private interface SqlAction<T> {
        T execute() throws SQLException;
    }
}
