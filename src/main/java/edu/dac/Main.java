package edu.dac;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class Main {
    private final BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    private final Console console = System.console();
    private final DacStore store;
    private final SecurityKernel kernel;

    private Main(DacStore store) {
        this.store = store;
        kernel = new SecurityKernel(store);
    }

    public static void main(String[] args) {
        String databasePath = "data/dac.sqlite3";
        boolean bootstrap = false;
        for (int index = 0; index < args.length; index++) {
            if ("--db".equals(args[index]) && index + 1 < args.length) {
                databasePath = args[++index];
            } else if ("--bootstrap-admin".equals(args[index])) {
                bootstrap = true;
            } else {
                System.err.println("Использование: java ... edu.dac.Main [--db ПУТЬ] [--bootstrap-admin]");
                return;
            }
        }

        try (DacStore store = new DacStore(databasePath)) {
            Main app = new Main(store);
            if (bootstrap) {
                app.bootstrapAdmin();
            } else {
                app.run();
            }
        } catch (SQLException | IOException exception) {
            System.err.println("Ошибка приложения: " + exception.getMessage());
        }
    }

    private void bootstrapAdmin() throws IOException, SQLException {
        String username = prompt("Имя первого администратора: ");
        char[] password = readPassword("Пароль администратора (не менее 8 символов): ");
        try {
            Session admin = store.bootstrapAdmin(username, password);
            System.out.println("Администратор " + admin.username() + " создан.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Не удалось создать администратора: " + exception.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void run() throws IOException {
        System.out.println("Учебная система DAC/ACL на Java и SQLite");
        while (true) {
            System.out.println("\n1) Регистрация  2) Вход  3) Выход");
            String choice = prompt("> ");
            try {
                if ("3".equals(choice)) {
                    return;
                }
                if ("1".equals(choice)) {
                    String username = prompt("Имя: ");
                    char[] password = readPassword("Пароль: ");
                    try {
                        Session created = store.register(username, password);
                        System.out.println("Создан пользователь " + created.username() + ". Войдите.");
                    } catch (IllegalArgumentException exception) {
                        System.out.println("Регистрация отклонена: " + exception.getMessage());
                    } catch (SQLException exception) {
                        System.out.println("Не удалось зарегистрировать пользователя: " + exception.getMessage());
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                    continue;
                }
                if (!"2".equals(choice)) {
                    System.out.println("Выберите 1, 2 или 3.");
                    continue;
                }
                String username = prompt("Имя: ");
                char[] password = readPassword("Пароль: ");
                Session session;
                try {
                    session = store.authenticate(username, password);
                } catch (AuthenticationException exception) {
                    System.out.println(exception.getMessage());
                    continue;
                } catch (SQLException exception) {
                    System.out.println("Ошибка входа: " + exception.getMessage());
                    continue;
                } finally {
                    Arrays.fill(password, '\0');
                }
                userMenu(session);
            } catch (IOException exception) {
                if (exception instanceof EndOfInputException) {
                    return;
                }
                System.out.println("Ошибка чтения ввода: " + exception.getMessage());
            }
        }
    }

    private void userMenu(Session session) throws IOException {
        while (true) {
            System.out.println("\n1) Список объектов  2) Создать  3) Читать  4) Записать");
            System.out.println("5) Удалить  6) Выдать ACL  7) Отозвать ACL  8) Матрица ACL");
            System.out.println("9) Симуляция трояна  10) Журнал (admin)  11) Режим аудита (admin)  0) Выход");
            String choice = prompt("> ");
            try {
                switch (choice) {
                    case "0" -> {
                        return;
                    }
                    case "1" -> {
                        for (DacStore.ObjectInfo object : kernel.listObjects(session)) {
                            System.out.printf("#%d %s (владелец: %s)%n",
                                    object.id(), object.name(), object.owner());
                        }
                    }
                    case "2" -> {
                        long id = kernel.createObject(session, prompt("Имя объекта: "),
                                prompt("Содержимое: "));
                        System.out.println("Создан объект #" + id + ".");
                    }
                    case "3" -> System.out.println(kernel.readObject(session, readObjectId()));
                    case "4" -> {
                        long id = readObjectId();
                        kernel.writeObject(session, id, prompt("Новое содержимое: "));
                        System.out.println("Содержимое обновлено.");
                    }
                    case "5" -> {
                        kernel.deleteObject(session, readObjectId());
                        System.out.println("Объект удалён.");
                    }
                    case "6", "7" -> {
                        long id = readObjectId();
                        String username = prompt("Пользователь: ");
                        Set<Permission> permissions = parsePermissions(prompt("Права (READ,WRITE,DELETE): "));
                        if ("6".equals(choice)) {
                            kernel.grant(session, id, username, permissions);
                        } else {
                            kernel.revoke(session, id, username, permissions);
                        }
                        System.out.println("ACL обновлён.");
                    }
                    case "8" -> {
                        for (DacStore.AclEntry entry : kernel.aclMatrix(session, readObjectId())) {
                            System.out.println(entry.username() + ": " + entry.permission());
                        }
                    }
                    case "9" -> {
                        String result = kernel.simulateTrojanRead(session, readObjectId());
                        System.out.println("Проверка прав: " + result
                                + ". Данные не читались и не передавались.");
                    }
                    case "10" -> {
                        for (DacStore.AuditEntry entry : kernel.auditLog(session, 100)) {
                            System.out.println(entry);
                        }
                    }
                    case "11" -> {
                        AuditMode mode = AuditMode.valueOf(prompt("Режим (ALL, SECURITY, OFF): ")
                                .trim().toUpperCase());
                        kernel.setAuditMode(session, mode);
                        System.out.println("Режим аудита установлен: " + mode + ".");
                    }
                    default -> System.out.println("Неизвестная команда.");
                }
            } catch (IllegalArgumentException exception) {
                System.out.println("Операция отклонена: " + exception.getMessage());
            } catch (AccessDeniedException exception) {
                System.out.println("Операция отклонена: " + exception.getMessage());
            } catch (SQLException exception) {
                System.out.println("Ошибка хранилища: " + exception.getMessage());
            }
        }
    }

    private long readObjectId() throws IOException {
        return Long.parseLong(prompt("ID объекта: ").trim());
    }

    private static Set<Permission> parsePermissions(String value) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String item : value.split(",")) {
            permissions.add(Permission.valueOf(item.trim().toUpperCase()));
        }
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("Нужно указать хотя бы одно право.");
        }
        return permissions;
    }

    private String prompt(String message) throws IOException {
        if (console != null) {
            String value = console.readLine("%s", message);
            if (value == null) {
                throw new EndOfInputException();
            }
            return value;
        }
        System.out.print(message);
        String value = input.readLine();
        if (value == null) {
            throw new EndOfInputException();
        }
        return value;
    }

    private char[] readPassword(String message) throws IOException {
        if (console != null) {
            char[] password = console.readPassword("%s", message);
            if (password == null) {
                throw new EndOfInputException();
            }
            return password;
        }
        System.out.println("Внимание: пароль будет виден при вводе (терминал без Console).");
        return prompt(message).toCharArray();
    }

    private static final class EndOfInputException extends IOException {
        private EndOfInputException() {
            super("Ввод завершён.");
        }
    }
}
