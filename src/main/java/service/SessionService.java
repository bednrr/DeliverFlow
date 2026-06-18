package service;

import model.User;
import util.ValidationException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionService {
    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        return token;
    }

    public User requireUser(String token) {
        if (token == null || token.isBlank() || !sessions.containsKey(token)) {
            throw new ValidationException("Sesja wygasła albo użytkownik nie jest zalogowany.");
        }
        return sessions.get(token);
    }

    public void refresh(String token, User user) {
        if (token != null && sessions.containsKey(token)) {
            sessions.put(token, user);
        }
    }
}
