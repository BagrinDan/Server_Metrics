package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.MessageDTO;
import org.example.entity.SystemMetricDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.border.SoftBevelBorder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.entity.enums.EventTypeEnum.NEW_SUBSCRIPTION;

/*
*  Данный класс работает с клиентами: pub / sub
*  Отвечает за получения сообщении, за отправку и десериализацию (JSON)
*/
public class ClientHandler implements Runnable{
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ConcurrentHashMap<String, Set<ClientHandler>> subscriptions;
    private PrintWriter printWriter;
    private String subTopic;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ClientHandler(Socket socket,
                         ConcurrentHashMap<String, Set<ClientHandler>> subscriptions){
        this.socket = socket;
        this.subscriptions = subscriptions;
    }

    @Override
    public void run() {
        try (
                // Нужен для получения сообщении через сокет:
                // 1. Получаем сырые байты
                // 2. Декодируем символы
                // 3. Собираем строку в фразу
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Нужен для отправки сообщении через сокет
            // Тот же прикол что и с BufferReader, то если первый нужен для обработки полученых сообщени, то PrintWriter для
            // получения
            printWriter = new PrintWriter(socket.getOutputStream(), true);
            printWriter.println("CONNECTED_OK");

            // Нужен чтобы держать соединение и получать сообщения от пабсов/сабсов
            String line;
            while((line = bufferedReader.readLine()) != null){
                line = line.trim();
                if(line.isEmpty()) continue;

                log.debug("[ClientHandler | Debug] Got client command: {}", line);

                if(line.startsWith("SUB:")){
                    String topic = line.substring(4).trim();

                    if (!topic.isEmpty()) {
                        this.subTopic = topic;
                        subscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(this);
                        printWriter.println("SUBSCRIBED_OK " + topic);
                        log.info("[ClientHandler | Debug] Client [{}] subscribed on topic: {}", socket.getRemoteSocketAddress(), topic);

                        // --- СЕРИАЛИЗАЦИЯ И ОТПРАВКА МЕТРИК ---
                        // Создаем объект с метрикой
                        SystemMetricDto metric = new SystemMetricDto(
                                NEW_SUBSCRIPTION,
                                subscriptions.get(topic).size(),
                                Runtime.getRuntime().totalMemory() / (1024.0 * 1024.0),
                                System.currentTimeMillis()
                        );

                        try {
                            // Сериализация
                            String jsonMetric = objectMapper.writeValueAsString(metric);

                            // 2. ОТПРАВКА: Отпрвляем метрику подписчикам "system/metrics"
                            multicast("system/metrics", jsonMetric);

                        } catch (JsonProcessingException e) {
                            log.error("[ClientHandler | ERROR] Failed to serialize metric: {}", e.getMessage());
                        }
                    }
                }
                else if(line.startsWith("PUB:")){
                    String[] parts = line.substring(4).split(":", 2);

                    if(parts.length == 2){
                        String topic = parts[0].trim();
                        String payload = parts[1].trim();

                        try{
                            MessageDTO dto = objectMapper.readValue(payload, MessageDTO.class);
                            log.info("[ClientHandler | INFO]: Topic [{}], Sender: {}, Content: {}", topic, dto.topic(), dto.payload());

                            String serializedPayload = objectMapper.writeValueAsString(dto);
                            multicast(topic, serializedPayload);

                            printWriter.println("PUBLISHED_OK");
                        } catch (JsonProcessingException e){
                            log.error("[ClientHandler | ERROR]: {}", e.getMessage());
                            printWriter.println("ERROR: Invalid JSON structure");
                        }

                    } else{
                        printWriter.println("[ClientHandler | ERROR]: Invalid PUB format. Use PUB:topic:payload");
                    }
                }
                else {
                    printWriter.println("[ClientHandler | ERROR]: Unknown command");
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

    // Метод для отправки сообщении определнным группам
    private void multicast(String topic, String payload){
        Set<ClientHandler> subscribers = subscriptions.get(topic);

        if(subscribers != null && !subscriptions.isEmpty()){
            String message = "MSG " + topic + " " + payload;
            for(ClientHandler sub : subscribers){
                sub.sendMessage(message);
            }
            log.debug("[ClientHandler | DEBIG] Message send {} to subscribers with topic {}", subscribers.size(), topic);
        }
    }

    // Мистер Проппер
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
