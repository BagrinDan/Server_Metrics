package org.example.service;

import org.example.service.inteface.BrokerServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/*
*  Daemon Broker Server.
*  Open's socket and listening for connections
*/


public class BrokerServerImpl implements BrokerServer {
    private static final Logger log = LoggerFactory.getLogger(BrokerServerImpl.class);
    private int port = 8080; // Default port ofr most Java programs

    private final ConcurrentHashMap<String, Set<ClientHandler>> subscriptions = new ConcurrentHashMap<>();
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public BrokerServerImpl(int port){
        this.port = port;
        log.info("[BrokerServer | INFO]: Server will run on port {}", port);
    }

    public BrokerServerImpl(){
        log.info("[BrokerServer | INFO]: Server will run on default port 8080");
    }

    @Override
    public void start_con() {
        log.debug("[BrokerServer |  DEBUG]: Starting connection...");
        threadPool = Executors.newCachedThreadPool();

        try {
            serverSocket = new ServerSocket(this.port);
            running = true;
            log.info("[BrokerServer | INFO]: Connection established on port: {}", port);

            while(running){
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new ClientHandler(clientSocket, subscriptions));
            }
        } catch (IOException e){
            if (running){
                log.error("[BrokerServer | ERROR]: Error at starting connection: {}", e.getMessage());
            }
        }
    }

    @Override
    public void stop_con() {
        log.debug("[BrokerServer |  DEBUG]: Stoping connection...");

        try{
            running = false;
            if(serverSocket != null) serverSocket.close();
            if(threadPool != null) threadPool.shutdown();
        } catch (IOException e){
            log.error("[BrokerServer | ERROR]: Error at closing connection: {}", e.getMessage());
        }

        log.debug("[BrokerServer |  DEBUG]: Connection is terminated");
    }
}
