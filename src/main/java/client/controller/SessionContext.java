package client.controller;

import client.network.ApiClient;
import model.User;

public class SessionContext {
    private final ApiClient apiClient;
    private String token;
    private User user;

    public SessionContext(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient apiClient() {
        return apiClient;
    }

    public String token() {
        return token;
    }

    public User user() {
        return user;
    }

    public void login(String token, User user) {
        this.token = token;
        this.user = user;
    }

    public void updateUser(User user) {
        this.user = user;
    }

    public void logout() {
        this.token = null;
        this.user = null;
    }
}
