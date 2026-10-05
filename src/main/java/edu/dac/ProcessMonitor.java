package edu.dac;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProcessMonitor implements AutoCloseable {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SecurityKernel kernel;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dac-lab-process-monitor");
        thread.setDaemon(true);
        return thread;
    });
    private final CopyOnWriteArrayList<ProcessEvent> events = new CopyOnWriteArrayList<>();

    public ProcessMonitor(SecurityKernel kernel) {
        this.kernel = kernel;
    }

    public void submitRead(Session session, long objectId) {
        submit(session, objectId, false);
    }

    public void submitTrojanSimulation(Session session, long objectId) {
        submit(session, objectId, true);
    }

    public List<ProcessEvent> events() {
        return List.copyOf(events);
    }

    private void submit(Session session, long objectId, boolean simulatedMalware) {
        String processName = simulatedMalware ? "TrojanDemo (симуляция)" : "DocumentReader (симуляция)";
        long pid = System.nanoTime();
        events.add(new ProcessEvent(pid, now(), processName, session.username(), objectId,
                "В очереди", "Ожидание запуска"));
        worker.submit(() -> {
            update(pid, "Работает", "Запрос передан ядру безопасности");
            try {
                String result;
                if (simulatedMalware) {
                    result = kernel.simulateTrojanRead(session, objectId);
                } else {
                    kernel.readObject(session, objectId);
                    result = "SUCCESS";
                }
                update(pid, "Завершён", simulatedMalware
                        ? "Безопасная проверка: " + result + ", данные не читались"
                        : "Решение ядра: " + result);
            } catch (AccessDeniedException exception) {
                update(pid, "Заблокирован", exception.getMessage());
            } catch (SQLException | RuntimeException exception) {
                update(pid, "Ошибка", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        });
    }

    private void update(long pid, String state, String details) {
        for (int index = events.size() - 1; index >= 0; index--) {
            ProcessEvent current = events.get(index);
            if (current.pid() == pid) {
                events.set(index, new ProcessEvent(pid, current.startedAt(), current.processName(),
                        current.actor(), current.objectId(), state, details));
                return;
            }
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }

    public record ProcessEvent(long pid, String startedAt, String processName, String actor,
                               long objectId, String state, String details) {
    }
}
