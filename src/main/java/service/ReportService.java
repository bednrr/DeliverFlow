package service;

import model.EventLog;
import repository.EventLogRepository;
import util.AppConfig;
import util.ServiceException;
import util.TimeUtil;
import util.ValidationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public class ReportService {
    private final EventLogRepository eventLogRepository;
    private final Path reportsDir;
    private final Path currentReport;
    private final Object lock = new Object();

    public ReportService(EventLogRepository eventLogRepository, AppConfig config) {
        this.eventLogRepository = eventLogRepository;
        this.reportsDir = config.reportsPath();
        try {
            Files.createDirectories(reportsDir);
        } catch (IOException e) {
            throw new ServiceException("Nie można utworzyć katalogu raportów.", e);
        }
        this.currentReport = reportsDir.resolve("report_" + LocalDateTime.now().format(TimeUtil.REPORT_FILE_FORMAT) + ".txt");
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void log(String level, String message) {
        synchronized (lock) {
            eventLogRepository.add(level, message);
            String line = "[" + LocalDateTime.now().format(TimeUtil.DISPLAY_FORMAT) + "] [" + level + "] " + message + System.lineSeparator();
            try {
                String existingContent = Files.exists(currentReport)
                        ? Files.readString(currentReport, StandardCharsets.UTF_8)
                        : "";
                Files.writeString(currentReport, line + existingContent, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new ServiceException("Nie można zapisać raportu.", e);
            }
        }
    }

    public List<EventLog> latestEvents(int limit) {
        int queryLimit = Math.max(limit * 4, limit);
        return eventLogRepository.latest(queryLimit).stream()
                .filter(event -> !isSimulationStepEvent(event))
                .limit(limit)
                .toList();
    }

    public List<EventLog> latestOperationalEvents(int limit) {
        int queryLimit = Math.max(limit * 4, limit);
        return eventLogRepository.latest(queryLimit).stream()
                .filter(event -> !isSimulationStepEvent(event))
                .filter(this::isOperationalEvent)
                .limit(limit)
                .toList();
    }

    public List<String> listReports() {
        return listReports(false);
    }

    public List<String> listReports(boolean operationalOnly) {
        try {
            Files.createDirectories(reportsDir);
            try (var stream = Files.list(reportsDir)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".txt"))
                        .map(path -> path.getFileName().toString())
                        .filter(fileName -> !operationalOnly || fileName.startsWith("report_operational_"))
                        .sorted(Comparator.reverseOrder())
                        .toList();
            }
        } catch (IOException e) {
            throw new ServiceException("Nie można odczytać listy raportów.", e);
        }
    }

    public String readReport(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.endsWith(".txt")) {
            throw new ValidationException("Nieprawidłowa nazwa raportu.");
        }
        Path base = reportsDir.toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        if (!target.startsWith(base)) {
            throw new ValidationException("Nieprawidłowa ścieżka raportu.");
        }
        try {
            if (!Files.exists(target)) {
                throw new ValidationException("Raport nie istnieje.");
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("Nie można odczytać raportu.", e);
        }
    }

    public String readOperationalReport(String fileName) {
        if (fileName == null || !fileName.startsWith("report_operational_")) {
            throw new ValidationException("Dyspozytor może otwierać tylko raporty operacyjne.");
        }
        return readReport(fileName);
    }

    public String generateManualReport(String actor) {
        return generateManualReport(actor, false);
    }

    public String generateOperationalManualReport(String actor) {
        return generateManualReport(actor, true);
    }

    private String generateManualReport(String actor, boolean operationalOnly) {
        String fileName = (operationalOnly ? "report_operational_" : "report_manual_")
                + LocalDateTime.now().format(TimeUtil.REPORT_FILE_FORMAT) + ".txt";
        Path target = reportsDir.resolve(fileName);
        StringBuilder builder = new StringBuilder();
        builder.append(operationalOnly
                ? "DeliverFlow: raport operacyjny"
                : "DeliverFlow: raport ręczny").append(System.lineSeparator());
        builder.append("Wygenerował: ").append(actor).append(System.lineSeparator());
        builder.append("Data: ").append(LocalDateTime.now().format(TimeUtil.DISPLAY_FORMAT)).append(System.lineSeparator());
        builder.append(System.lineSeparator()).append(operationalOnly ? "Ostatnie zdarzenia operacyjne:" : "Ostatnie zdarzenia:")
                .append(System.lineSeparator());
        List<EventLog> events = operationalOnly ? latestOperationalEvents(100) : latestEvents(100);
        for (EventLog event : events) {
            builder.append(TimeUtil.formatDisplay(event.getCreatedAt()))
                    .append(" [").append(event.getLevel()).append("] ")
                    .append(event.getMessage())
                    .append(System.lineSeparator());
        }
        try {
            Files.writeString(target, builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ServiceException("Nie można wygenerować raportu.", e);
        }
        info("Wygenerowano raport ręczny: " + fileName);
        return fileName;
    }

    private boolean isOperationalEvent(EventLog event) {
        String message = event.getMessage() == null ? "" : event.getMessage().toLowerCase();
        if (message.contains("logowanie")
                || message.contains("ustawienia konta")
                || message.contains("zarejestrowano klienta")
                || message.contains("administrator zapisał konto")
                || message.contains("konto")) {
            return false;
        }
        return message.contains("paczk")
                || message.contains("kurier")
                || message.contains("dyspozycj")
                || message.contains("symulac")
                || message.contains("zlecen");
    }

    private boolean isSimulationStepEvent(EventLog event) {
        if (event == null || event.getMessage() == null) {
            return false;
        }
        return isSimulationStepText(event.getMessage());
    }

    private boolean isSimulationStepText(String text) {
        String message = text == null ? "" : text.trim().toLowerCase();
        return message.contains("symulacja wykonała") && message.contains(" krok");
    }

}
