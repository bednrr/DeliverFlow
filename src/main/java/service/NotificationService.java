package service;

import model.Notification;
import model.User;
import model.UserRole;
import repository.NotificationRepository;
import repository.UserRepository;
import util.ValidationException;

import java.util.Comparator;
import java.util.List;

public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               ReportService reportService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.reportService = reportService;
    }

    public List<Notification> listForUser(User user) {
        return notificationRepository.findForRecipient(user.getId());
    }

    public List<User> recipients(User sender) {
        requireSender(sender);
        return userRepository.findAll().stream()
                .filter(user -> user.getId() != sender.getId())
                .filter(user -> !user.isBlocked())
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Notification send(User sender, long recipientUserId, String title, String message) {
        requireSender(sender);
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono odbiorcy powiadomienia."));
        if (recipient.isBlocked()) {
            throw new ValidationException("Nie można wysłać powiadomienia do zablokowanego konta.");
        }
        String normalizedTitle = requireText(title, "Podaj tytuł powiadomienia.", 120);
        String normalizedMessage = requireText(message, "Podaj treść powiadomienia.", 1000);
        Notification notification = notificationRepository.create(sender.getId(), recipientUserId,
                normalizedTitle, normalizedMessage);
        reportService.info("Użytkownik " + sender.getEmail() + " wysłał powiadomienie do " + recipient.getEmail() + ".");
        return notification;
    }

    public Notification markRead(User user, long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono powiadomienia."));
        if (notification.getRecipientUserId() != user.getId()) {
            throw new ValidationException("Nie możesz oznaczyć cudzego powiadomienia.");
        }
        notificationRepository.markRead(notificationId, user.getId());
        return notificationRepository.findById(notificationId).orElseThrow();
    }

    public List<Notification> markAllRead(User user) {
        notificationRepository.markAllRead(user.getId());
        return listForUser(user);
    }

    private void requireSender(User sender) {
        if (sender == null) {
            throw new ValidationException("Nie znaleziono nadawcy powiadomienia.");
        }
        if (sender.getRole() == UserRole.CLIENT) {
            throw new ValidationException("Klient nie może wysyłać powiadomień.");
        }
    }

    private String requireText(String value, String missingMessage, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(missingMessage);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ValidationException("Tekst jest za długi. Limit znaków: " + maxLength + ".");
        }
        return normalized;
    }
}
