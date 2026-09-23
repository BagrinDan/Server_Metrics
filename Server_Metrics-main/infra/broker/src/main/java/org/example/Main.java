package org.example;

import org.example.service.BrokerServerImpl;
import org.example.service.inteface.BrokerServer;

public class Main {
    public static void main(String[] args) {
        String version = "Suicide squirrel | ver:0.3";

        System.out.println("------ Welcome to ServerMatrix | " + version + " ---------");

        BrokerServer server = new BrokerServerImpl();
        server.start_con();
    }
}

// TODO: Console menu
