package service;

import model.Theme;
import model.User;
import model.UserRole;
import repository.UserRepository;
import util.PhoneUtil;
import util.ValidationException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User login(String email, String password) {
        requireNotBlank(email, "Adres e-mail nie może być pusty.");
        requireNotBlank(password, "Hasło nie może być puste.");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ValidationException("Nieprawidłowy adres e-mail albo hasło."));
        if (!user.getPassword().equals(password)) {
            throw new ValidationException("Nieprawidłowy adres e-mail albo hasło.");
        }
        if (user.isBlocked()) {
            throw new ValidationException("Konto jest zablokowane.");
        }
        return user;
    }

    public User registerClient(String firstName, String lastName, String email, String phone,
                               String password, String repeatPassword) {
        requireNotBlank(firstName, "Imię nie może być puste.");
        requireNotBlank(lastName, "Nazwisko nie może być puste.");
        validateRegistration(email, phone, password, repeatPassword);
        String normalizedPhone = PhoneUtil.requireValid(phone);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ValidationException("Konto z tym adresem e-mail już istnieje.");
        }
        User user = new User();
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setEmail(email);
        user.setPhone(normalizedPhone);
        user.setPassword(password);
        user.setRole(UserRole.CLIENT);
        user.setTheme(Theme.LIGHT);
        user.setBlocked(false);
        return userRepository.save(user);
    }

    public User saveUser(Long id, String firstName, String lastName, String email, String phone, String password,
                         UserRole role, Theme theme, boolean blocked) {
        requireNotBlank(firstName, "Imię nie może być puste.");
        requireNotBlank(lastName, "Nazwisko nie może być puste.");
        validateEmail(email);
        String normalizedPhone = PhoneUtil.requireValid(phone);
        if (role == null) {
            throw new ValidationException("Rola użytkownika jest wymagana.");
        }
        if (id == null) {
            requireNotBlank(password, "Hasło nie może być puste.");
            if (userRepository.findByEmail(email).isPresent()) {
                throw new ValidationException("Konto z tym adresem e-mail już istnieje.");
            }
            User user = new User();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setPhone(normalizedPhone);
            user.setPassword(password);
            user.setRole(role);
            user.setTheme(theme == null ? Theme.LIGHT : theme);
            user.setBlocked(blocked);
            return userRepository.save(user);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ValidationException("Nie znaleziono użytkownika."));
        if (userRepository.emailExistsForAnotherUser(email, id)) {
            throw new ValidationException("Konto z tym adresem e-mail już istnieje.");
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(normalizedPhone);
        user.setRole(role);
        user.setTheme(theme == null ? Theme.LIGHT : theme);
        user.setBlocked(blocked);
        userRepository.update(user);
        return userRepository.findById(id).orElseThrow();
    }

    public User updateAccount(User currentUser, String firstName, String lastName, String email, String phone, Theme theme,
                              String oldPassword, String newPassword, String repeatNewPassword) {
        requireNotBlank(firstName, "Imię nie może być puste.");
        requireNotBlank(lastName, "Nazwisko nie może być puste.");
        validateEmail(email);
        String normalizedPhone = PhoneUtil.requireValid(phone);
        if (userRepository.emailExistsForAnotherUser(email, currentUser.getId())) {
            throw new ValidationException("Konto z tym adresem e-mail już istnieje.");
        }
        currentUser.setFirstName(firstName.trim());
        currentUser.setLastName(lastName.trim());
        currentUser.setEmail(email);
        currentUser.setPhone(normalizedPhone);
        if (passwordChangeRequested(oldPassword, newPassword, repeatNewPassword)) {
            requireNotBlank(oldPassword, "Stare hasło jest wymagane.");
            requireNotBlank(newPassword, "Nowe hasło jest wymagane.");
            requireNotBlank(repeatNewPassword, "Powtórzenie nowego hasła jest wymagane.");
            if (!currentUser.getPassword().equals(oldPassword)) {
                throw new ValidationException("Stare hasło jest nieprawidłowe.");
            }
            if (!newPassword.equals(repeatNewPassword)) {
                throw new ValidationException("Nowe hasło i powtórzenie muszą być identyczne.");
            }
            currentUser.setPassword(newPassword);
        }
        currentUser.setTheme(theme == null ? currentUser.getTheme() : theme);
        userRepository.update(currentUser);
        return userRepository.findById(currentUser.getId()).orElseThrow();
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public void requireRole(User user, UserRole... roles) {
        boolean allowed = Arrays.stream(roles).anyMatch(role -> role == user.getRole());
        if (!allowed) {
            throw new ValidationException("Brak uprawnień do tej operacji.");
        }
    }

    private void validateRegistration(String email, String phone, String password, String repeatPassword) {
        validateEmail(email);
        PhoneUtil.requireValid(phone);
        requireNotBlank(password, "Hasło nie może być puste.");
        if (!password.equals(repeatPassword)) {
            throw new ValidationException("Hasło i powtórzenie hasła muszą być identyczne.");
        }
    }

    private void validateEmail(String email) {
        requireNotBlank(email, "Adres e-mail nie może być pusty.");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Adres e-mail ma niepoprawny format.");
        }
    }

    private void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
    }

    private boolean passwordChangeRequested(String oldPassword, String newPassword, String repeatNewPassword) {
        return hasText(oldPassword) || hasText(newPassword) || hasText(repeatNewPassword);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
