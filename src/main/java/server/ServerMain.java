package server;

import service.ServiceRegistry;
import util.AppConfig;

public class ServerMain {
    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        ServiceRegistry services = new ServiceRegistry(config);
        DeliveryServer server = new DeliveryServer(services);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.startBlocking();
    }
}
