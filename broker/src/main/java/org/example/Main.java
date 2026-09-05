package org.example;

import org.example.service.BrokerServerImpl;
import org.example.service.inteface.BrokerServer;

public class Main {
    public static void main(String[] args) {
        String version = "Suicide squirrel | ver:0.1";

        BrokerServer server = new BrokerServerImpl();
    }
}