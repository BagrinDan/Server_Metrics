package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable{
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ConcurrentHashMap<String, Set<ClientHandler>> subscriptions;
    private PrintWriter printWriter;
    private String subTopic;

    public ClientHandler(Socket socket,
                         ConcurrentHashMap<String, Set<ClientHandler>> subscriptions){
        this.socket = socket;
        this.subscriptions = subscriptions;
    }

    @Override
    public void run() {
        try (
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            printWriter = new PrintWriter(socket.getOutputStream(), true);
            printWriter.println("CONNECTED_OK");

            String line;
            while((line = bufferedReader.readLine()) != null){
                line = line.trim();
                if(line.isEmpty()) continue;

                log.debug("[ClientHandler | Debug] Got client command: {}", line);

                if(line.startsWith("SUB:")){
                    String topic = line.substring(4).trim();

                    if(!topic.isEmpty()){
                        this.subTopic = topic;
                        subscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(this);
                        printWriter.println("SUBSCRIBED_OK " + topic);
                        log.info("[ClientHandler | Debug] Client [{}] subscribed on topic: {}", socket.getRemoteSocketAddress(), topic);
                    }
                }
                else if(line.startsWith("PUB:")){
                    String[] parts = line.substring(4).split(":", 2);

                    if(parts.length == 2){
                        String topic = parts[0].trim();
                        String payload = parts[1].trim();

                        log.info("[ClientHandler | INFO]: [{}]: {}", topic, payload);
                        broadcast(topic, payload);
                        printWriter.println("PUBLISHED_OK");
                    } else{
                        printWriter.println("ERROR: Invalid PUB format. Use PUB:topic:payload");
                    }
                }
                else {
                    printWriter.println("ERROR: Unknown command");
                }
            }
        } catch (IOException e){
            log.info("[ClientHandler | INFO] Connection with client is lost {}", e.getMessage());
        } finally {
            cleanup();
        }
    }

    public void sendMessage(String message){
        if (printWriter != null){
            printWriter.println(message);
        }
    }

    private void broadcast(String topic, String payload){
        Set<ClientHandler> subscribers = subscriptions.get(topic);

        if(subscribers != null && !subscriptions.isEmpty()){
            String message = "MSG " + topic + " " + payload;
            for(ClientHandler sub : subscribers){
                sub.sendMessage(message);
            }
            log.debug("[ClientHandler | DEBIG] Message send {} to subscribers with topic {}", subscribers.size(), topic);
        }
    }

    private void cleanup(){
        if(subTopic != null){
            Set<ClientHandler> subscribers = subscriptions.get(subTopic);

            if(subscribers != null){
                subscribers.remove(this);
                log.info("[ClientHandler | DEBUG] Subscribers {} is deleted from subs topic {}", subscriptions.get(subTopic), subTopic);
            }
        }

        try {
            if(socket != null && !socket.isClosed()){
                socket.close();
            }
        } catch (IOException e){
            log.error("[ClientHandler | ERROR] Error while closing client connection: {}", e.getMessage());
        }
    }
}
