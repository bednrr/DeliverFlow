package server;

import service.ServiceRegistry;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DeliveryServer {
    private final ServiceRegistry services;
    private final ExecutorService clientExecutor;
    private final RequestRouter router;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public DeliveryServer(ServiceRegistry services) {
        this.services = services;
        this.clientExecutor = Executors.newFixedThreadPool(services.config().serverMaxThreads());
        this.router = new RequestRouter(services);
    }

    public void startBlocking() {
        running = true;
        try (ServerSocket localServerSocket = new ServerSocket(services.config().serverPort())) {
            this.serverSocket = localServerSocket;
            services.reportService().info("Start serwera DeliverFlow na porcie " + services.config().serverPort() + ".");
            while (running) {
                try {
                    Socket socket = localServerSocket.accept();
                    clientExecutor.submit(new ClientHandler(socket, router, services.reportService()));
                } catch (IOException e) {
                    if (running) {
                        services.reportService().error("Błąd przyjmowania połączenia: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            services.reportService().error("Nie można uruchomić serwera: " + e.getMessage());
            throw new IllegalStateException("Nie można uruchomić serwera.", e);
        } finally {
            stop();
        }
    }

    public void startAsync() {
        Thread thread = new Thread(this::startBlocking, "deliverflow-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        services.simulationService().shutdown();
        clientExecutor.shutdown();
        try {
            if (!clientExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                clientExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            clientExecutor.shutdownNow();
        }
        services.reportService().info("Zatrzymanie serwera DeliverFlow.");
    }
}
