package edu.dac;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public final class Main {
    private final DacStore store;
    private final SecurityKernel kernel;
    private JFrame frame;
    private Session session;
    private ProcessMonitor processMonitor;
    private JTable objectsTable;
    private DefaultTableModel objectsModel;
    private JTable processTable;
    private DefaultTableModel processModel;
    private JTable auditTable;
    private DefaultTableModel auditModel;
    private JTable usersTable;
    private DefaultTableModel usersModel;
    private JComboBox<SecurityLevel> clearanceSelector;
    private JComboBox<AuditMode> auditModeSelector;
    private Timer processRefreshTimer;

    private Main(DacStore store) {
        this.store = store;
        kernel = new SecurityKernel(store);
    }

    public static void main(String[] args) {
        String databasePath = "data/dac.sqlite3";
        boolean bootstrapAdmin = false;
        boolean seedDemo = false;
        for (int index = 0; index < args.length; index++) {
            if ("--db".equals(args[index]) && index + 1 < args.length) {
                databasePath = args[++index];
            } else if ("--bootstrap-admin".equals(args[index])) {
                bootstrapAdmin = true;
            } else if ("--seed-demo".equals(args[index])) {
                seedDemo = true;
            } else {
                System.err.println("Параметры: --db ПУТЬ, --bootstrap-admin, --seed-demo");
                return;
            }
        }
        try {
            DacStore store = new DacStore(databasePath);
            if (bootstrapAdmin || seedDemo) {
                try (DacStore setupStore = store) {
                    if (seedDemo) {
                        DemoData.seed(setupStore);
                    } else {
                        createFirstAdmin(setupStore);
                    }
                }
                if (bootstrapAdmin) {
                    return;
                }
                store = new DacStore(databasePath);
            }
            DacStore applicationStore = store;
            SwingUtilities.invokeLater(() -> new Main(applicationStore).showLogin());
        } catch (Exception exception) {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                System.err.println("Не удалось запустить приложение: " + exception.getMessage());
            } else {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        exception.getMessage(), "Ошибка запуска", JOptionPane.ERROR_MESSAGE));
            }
        }
    }

    private static void createFirstAdmin(DacStore store) throws SQLException {
        java.io.Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Для создания администратора запустите программу в терминале.");
        }
        String username = console.readLine("Имя первого администратора: ");
        char[] password = console.readPassword("Пароль (не менее 8 символов): ");
        try {
            store.bootstrapAdmin(username, password);
            System.out.println("Администратор создан. Запустите приложение без --bootstrap-admin.");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void showLogin() {
        JFrame loginFrame = new JFrame("Лабораторная работа — Белл–ЛаПадула");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setMinimumSize(new Dimension(470, 370));
        loginFrame.setLocationRelativeTo(null);
        try {
            if (!store.hasAdministrator()) {
                bootstrapAdminDialog(loginFrame);
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(loginFrame, rootMessage(exception),
                    "Ошибка SQLite", JOptionPane.ERROR_MESSAGE);
        }
        JTabbedPane forms = new JTabbedPane();
        forms.addTab("Вход", authenticationPanel(loginFrame, false));
        forms.addTab("Регистрация", authenticationPanel(loginFrame, true));
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel title = new JLabel("Модель доступа Белла–ЛаПадулы", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(18f));
        root.add(title, BorderLayout.NORTH);
        root.add(forms, BorderLayout.CENTER);
        JLabel note = new JLabel("<html><center>DAC / ACL + MAC · No Read Up · No Write Down</center></html>",
                SwingConstants.CENTER);
        root.add(note, BorderLayout.SOUTH);
        loginFrame.setContentPane(root);
        loginFrame.pack();
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
    }

    private void bootstrapAdminDialog(JFrame parent) {
        JTextField username = new JTextField(20);
        JPasswordField password = new JPasswordField(20);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Имя первого администратора"), c);
        c.gridx = 1;
        form.add(username, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Пароль (минимум 8 символов)"), c);
        c.gridx = 1;
        form.add(password, c);
        if (JOptionPane.showConfirmDialog(parent, form, "Первый запуск: создать администратора",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            Arrays.fill(password.getPassword(), '\0');
            return;
        }
        char[] secret = password.getPassword();
        try {
            store.bootstrapAdmin(username.getText().trim(), secret);
            JOptionPane.showMessageDialog(parent, "Администратор создан. Его допуск — TOP SECRET.");
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(parent, rootMessage(exception),
                    "Не удалось создать администратора", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private JPanel authenticationPanel(JFrame loginFrame, boolean registration) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Имя пользователя"), constraints);
        JTextField usernameField = new JTextField(22);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(usernameField, constraints);
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel("Пароль"), constraints);
        JPasswordField passwordField = new JPasswordField(22);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(passwordField, constraints);
        JButton submit = new JButton(registration ? "Создать учётную запись" : "Войти");
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        panel.add(submit, constraints);
        JLabel hint = new JLabel(registration
                ? "Новый пользователь получит допуск «Несекретно»."
                : "Допуск и роль проверяются по базе данных при каждой операции.");
        constraints.gridy++;
        panel.add(hint, constraints);
        submit.addActionListener(event -> {
            char[] password = passwordField.getPassword();
            submit.setEnabled(false);
            new SwingWorker<Session, Void>() {
                @Override
                protected Session doInBackground() throws Exception {
                    if (registration) {
                        store.register(usernameField.getText().trim(), password);
                        return null;
                    }
                    return store.authenticate(usernameField.getText().trim(), password);
                }

                @Override
                protected void done() {
                    Arrays.fill(password, '\0');
                    submit.setEnabled(true);
                    try {
                        Session authenticated = get();
                        if (registration) {
                            passwordField.setText("");
                            JOptionPane.showMessageDialog(loginFrame, "Регистрация завершена. Теперь войдите.");
                        } else {
                            session = authenticated;
                            loginFrame.dispose();
                            showWorkspace();
                        }
                    } catch (Exception exception) {
                        JOptionPane.showMessageDialog(loginFrame, rootMessage(exception),
                                "Операция отклонена", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        return panel;
    }

    private void showWorkspace() {
        frame = new JFrame("DAC / ACL — Белл–ЛаПадула");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 650));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (processRefreshTimer != null) {
                    processRefreshTimer.stop();
                }
                if (processMonitor != null) {
                    processMonitor.close();
                }
                try {
                    store.close();
                } catch (SQLException exception) {
                    System.err.println("Не удалось закрыть SQLite: " + exception.getMessage());
                }
            }
        });
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Объекты и ACL", objectPanel());
        tabs.addTab("Монитор процессов", processPanel());
        tabs.addTab("Аудит", auditPanel());
        try {
            if (kernel.role(session) == Role.ADMIN) {
                tabs.addTab("Пользователи и допуски", usersPanel());
            }
            SecurityLevel clearance = kernel.clearance(session);
            frame.add(new JLabel("Пользователь: " + session.username()
                    + "    Роль: " + kernel.role(session)
                    + "    Допуск: " + clearance.displayName(), SwingConstants.CENTER), BorderLayout.NORTH);
        } catch (SQLException exception) {
            showError(exception);
        }
        frame.add(tabs, BorderLayout.CENTER);
        frame.setVisible(true);
        refreshObjects();
        refreshAudit();
    }

    private JPanel objectPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        objectsModel = model("ID", "Объект", "Владелец", "Классификация");
        objectsTable = new JTable(objectsModel);
        objectsTable.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(objectsTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton(actions, "Обновить", this::refreshObjects);
        addButton(actions, "Создать объект", this::createObject);
        addButton(actions, "Читать", this::readSelectedObject);
        addButton(actions, "Записать", this::writeSelectedObject);
        addButton(actions, "Удалить", this::deleteSelectedObject);
        addButton(actions, "Выдать ACL", () -> changeAcl(true));
        addButton(actions, "Отозвать ACL", () -> changeAcl(false));
        addButton(actions, "Показать ACL", this::showAcl);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(new JLabel("MAC действует дополнительно к ACL: чтение только вверх по уровню допуска, "
                + "запись — только на свой уровень или выше."), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel processPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        processModel = model("PID (учебный)", "Время", "Процесс", "Субъект",
                "ID объекта", "Статус", "Результат");
        processTable = new JTable(processModel);
        processTable.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(processTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton(actions, "Обычный процесс: READ", () -> submitProcess(false));
        addButton(actions, "Симуляция вредоносного процесса", () -> submitProcess(true));
        addButton(actions, "Очистить экран", () -> processModel.setRowCount(0));
        panel.add(actions, BorderLayout.NORTH);
        JTextArea explanation = new JTextArea("""
                Учебный монитор фоновых задач приложения (не монитор ОС).
                Вредоносная демонстрация безопасна: процесс запрашивает чтение через SecurityKernel.
                Ядро проверяет ACL и No Read Up, аудит фиксирует решение. Код не запускает команды,
                исполняемые файлы, сетевые запросы и не читает данные при симуляции атаки.
                """);
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setBackground(panel.getBackground());
        panel.add(explanation, BorderLayout.SOUTH);
        processMonitor = new ProcessMonitor(kernel);
        processRefreshTimer = new Timer(500, event -> refreshProcesses());
        processRefreshTimer.start();
        return panel;
    }

    private JPanel auditPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        auditModel = model("ID", "Время", "Субъект", "Операция", "Цель", "Результат", "Подробности");
        auditTable = new JTable(auditModel);
        auditTable.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(auditTable), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton(controls, "Обновить журнал", this::refreshAudit);
        try {
            if (kernel.role(session) == Role.ADMIN) {
                auditModeSelector = new JComboBox<>(AuditMode.values());
                auditModeSelector.setSelectedItem(store.auditMode());
                controls.add(new JLabel("Режим аудита:"));
                controls.add(auditModeSelector);
                addButton(controls, "Применить", this::updateAuditMode);
            }
        } catch (SQLException exception) {
            showError(exception);
        }
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JLabel("Режимы: ALL — все операции; SECURITY — события безопасности; OFF — аудит отключён."),
                BorderLayout.SOUTH);
        return panel;
    }

    private JPanel usersPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        usersModel = model("ID", "Пользователь", "Роль", "Допуск");
        usersTable = new JTable(usersModel);
        usersTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton(controls, "Обновить", this::refreshUsers);
        clearanceSelector = new JComboBox<>(SecurityLevel.values());
        controls.add(clearanceSelector);
        addButton(controls, "Назначить допуск выбранному", this::updateClearance);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JLabel("Только администратор может назначать уровень допуска пользователю."),
                BorderLayout.SOUTH);
        refreshUsers();
        return panel;
    }

    private void createObject() {
        JTextField name = new JTextField();
        JComboBox<SecurityLevel> classification = new JComboBox<>(SecurityLevel.values());
        JTextArea content = new JTextArea(8, 32);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Имя"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(name, c);
        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        form.add(new JLabel("Классификация"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(classification, c);
        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Содержимое"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(content), c);
        if (JOptionPane.showConfirmDialog(frame, form, "Новый объект",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            kernel.createObject(session, name.getText(), content.getText(),
                    (SecurityLevel) classification.getSelectedItem());
            refreshObjects();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void readSelectedObject() {
        withSelectedObject(object -> {
            String content = kernel.readObject(session, object.id());
            JTextArea text = new JTextArea(content, 12, 48);
            text.setEditable(false);
            JOptionPane.showMessageDialog(frame, new JScrollPane(text),
                    object.name() + " — " + object.classification().displayName(),
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void writeSelectedObject() {
        withSelectedObject(object -> {
            JTextArea text = new JTextArea(12, 48);
            text.setText("Введите полное новое содержимое объекта.");
            text.selectAll();
            if (JOptionPane.showConfirmDialog(frame, new JScrollPane(text), "Изменить " + object.name(),
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                kernel.writeObject(session, object.id(), text.getText());
                refreshObjects();
            }
        });
    }

    private void deleteSelectedObject() {
        withSelectedObject(object -> {
            if (JOptionPane.showConfirmDialog(frame, "Удалить объект «" + object.name() + "»?",
                    "Подтверждение", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                kernel.deleteObject(session, object.id());
                refreshObjects();
            }
        });
    }

    private void changeAcl(boolean grant) {
        withSelectedObject(object -> {
            JTextField username = new JTextField();
            JComboBox<Permission> permission = new JComboBox<>(Permission.values());
            JPanel form = new JPanel(new GridBagLayout());
            form.add(new JLabel("Пользователь"));
            form.add(username);
            form.add(new JLabel("Право"));
            form.add(permission);
            int choice = JOptionPane.showConfirmDialog(frame, form,
                    (grant ? "Выдать" : "Отозвать") + " право ACL", JOptionPane.OK_CANCEL_OPTION);
            if (choice == JOptionPane.OK_OPTION) {
                Set<Permission> selected = EnumSet.of((Permission) permission.getSelectedItem());
                if (grant) {
                    kernel.grant(session, object.id(), username.getText().trim(), selected);
                } else {
                    kernel.revoke(session, object.id(), username.getText().trim(), selected);
                }
                refreshObjects();
            }
        });
    }

    private void showAcl() {
        withSelectedObject(object -> {
            String text = kernel.aclMatrix(session, object.id()).stream()
                    .map(entry -> entry.username() + " — " + entry.permission())
                    .reduce((left, right) -> left + "\n" + right).orElse("ACL пока пуста.");
            JOptionPane.showMessageDialog(frame, text, "ACL: " + object.name(),
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void submitProcess(boolean maliciousSimulation) {
        DacStore.ObjectInfo object = selectedObject();
        if (object == null) {
            showError(new IllegalArgumentException("Сначала выберите объект."));
            return;
        }
        if (maliciousSimulation) {
            processMonitor.submitTrojanSimulation(session, object.id());
        } else {
            processMonitor.submitRead(session, object.id());
        }
        refreshProcesses();
    }

    private void updateAuditMode() {
        try {
            kernel.setAuditMode(session, (AuditMode) auditModeSelector.getSelectedItem());
            refreshAudit();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void updateClearance() {
        int row = usersTable.getSelectedRow();
        if (row < 0) {
            showError(new IllegalArgumentException("Сначала выберите пользователя."));
            return;
        }
        String username = usersModel.getValueAt(usersTable.convertRowIndexToModel(row), 1).toString();
        try {
            kernel.setClearance(session, username, (SecurityLevel) clearanceSelector.getSelectedItem());
            refreshUsers();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void refreshObjects() {
        if (objectsModel == null) {
            return;
        }
        try {
            objectsModel.setRowCount(0);
            for (DacStore.ObjectInfo object : kernel.listObjects(session)) {
                objectsModel.addRow(new Object[]{object.id(), object.name(), object.owner(),
                        object.classification().displayName()});
            }
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void refreshProcesses() {
        if (processModel == null || processMonitor == null) {
            return;
        }
        processModel.setRowCount(0);
        for (ProcessMonitor.ProcessEvent event : processMonitor.events()) {
            processModel.addRow(new Object[]{event.pid(), event.startedAt(), event.processName(),
                    event.actor(), event.objectId(), event.state(), event.details()});
        }
    }

    private void refreshAudit() {
        if (auditModel == null) {
            return;
        }
        try {
            auditModel.setRowCount(0);
            for (DacStore.AuditEntry entry : kernel.auditLog(session, 500)) {
                auditModel.addRow(new Object[]{entry.id(), entry.occurredAt(), entry.actor(),
                        entry.operation(), entry.target(), entry.result(), entry.details()});
            }
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void refreshUsers() {
        if (usersModel == null) {
            return;
        }
        try {
            usersModel.setRowCount(0);
            for (DacStore.UserInfo user : kernel.users(session)) {
                usersModel.addRow(new Object[]{user.id(), user.username(), user.role(),
                        user.clearance().displayName()});
            }
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private DacStore.ObjectInfo selectedObject() {
        int selected = objectsTable.getSelectedRow();
        if (selected < 0) {
            return null;
        }
        int row = objectsTable.convertRowIndexToModel(selected);
        long id = ((Number) objectsModel.getValueAt(row, 0)).longValue();
        String name = objectsModel.getValueAt(row, 1).toString();
        String owner = objectsModel.getValueAt(row, 2).toString();
        SecurityLevel label = java.util.Arrays.stream(SecurityLevel.values())
                .filter(level -> level.displayName().equals(objectsModel.getValueAt(row, 3)))
                .findFirst().orElse(SecurityLevel.UNCLASSIFIED);
        return new DacStore.ObjectInfo(id, name, owner, label);
    }

    private void withSelectedObject(SqlOperation operation) {
        DacStore.ObjectInfo object = selectedObject();
        if (object == null) {
            showError(new IllegalArgumentException("Сначала выберите объект."));
            return;
        }
        try {
            operation.run(object);
            refreshAudit();
        } catch (Exception exception) {
            showError(exception);
            refreshAudit();
        }
    }

    private void showError(Exception exception) {
        JOptionPane.showMessageDialog(frame, rootMessage(exception),
                "Операция отклонена", JOptionPane.ERROR_MESSAGE);
    }

    private static String rootMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.toString() : root.getMessage();
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static void addButton(JPanel panel, String title, Runnable action) {
        JButton button = new JButton(title);
        button.addActionListener(event -> action.run());
        panel.add(button);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run(DacStore.ObjectInfo object) throws Exception;
    }
}
