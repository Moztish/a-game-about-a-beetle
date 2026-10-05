package edu.dac;

import java.sql.SQLException;
import java.util.EnumSet;

public final class DemoData {
    private DemoData() {
    }

    public static void seed(DacStore store) throws SQLException {
        if (!store.hasNoUsers()) {
            throw new IllegalStateException(
                    "Тестовые данные загружаются только в пустую базу. Укажите отдельный путь --db.");
        }
        Session admin = store.bootstrapAdmin("admin", "AdminDemo123!".toCharArray());
        Session alice = store.register("alice", "AliceDemo123!".toCharArray());
        Session bob = store.register("bob", "BobDemo123!".toCharArray());
        Session carol = store.register("carol", "CarolDemo123!".toCharArray());
        SecurityKernel kernel = new SecurityKernel(store);
        kernel.setClearance(admin, "alice", SecurityLevel.CONFIDENTIAL);
        kernel.setClearance(admin, "carol", SecurityLevel.SECRET);
        long publicDocument = kernel.createObject(bob, "Объявление",
                "Открытый учебный документ.", SecurityLevel.UNCLASSIFIED);
        long confidentialDocument = kernel.createObject(alice, "Служебная записка",
                "Демонстрационный текст уровня CONFIDENTIAL.", SecurityLevel.CONFIDENTIAL);
        long secretDocument = kernel.createObject(carol, "Учебный секрет",
                "Искусственные данные: секретный объект для тестирования BLP.", SecurityLevel.SECRET);

        Session[] users = {alice, bob};
        long[] objects = {publicDocument, confidentialDocument, secretDocument};
        long[] owners = {bob.userId(), alice.userId(), carol.userId()};
        for (int objectIndex = 0; objectIndex < objects.length; objectIndex++) {
            for (Session user : users) {
                if (user.userId() == owners[objectIndex]) {
                    continue;
                }
                kernel.grant(admin, objects[objectIndex], user.username(),
                        EnumSet.of(Permission.READ, Permission.WRITE));
            }
        }
        kernel.setAuditMode(admin, AuditMode.ALL);
    }
}
