import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.MessageDTO;
import org.example.service.BrokerServerImpl;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
public class BrokerServerImplTest {
    private static final Logger log = LoggerFactory.getLogger(BrokerServerImplTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private BrokerServerImpl brokerServer;
    private final int testPort = 8090;
    private final String testIp = "127.0.0.1";

    @BeforeEach
    public void setup(){
        brokerServer = new BrokerServerImpl(testPort);

        Thread serverThread = new Thread(() -> brokerServer.start_con());
        serverThread.start();

        try{
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    public void stop(){
        if(brokerServer != null){
            brokerServer.stop_con();
        }
    }

    @Test
    @Tag("TEST_1")
    public void testServerAcceptsConnection() {
        try (Socket socket = new Socket(testIp, testPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String response = reader.readLine();

            assertNotNull(response, "Server must answer to clients");
            assertTrue(response.contains("CONNECTED_OK"), "Answer must be CONNECTED_OK");

        } catch (Exception e) {
            fail("[TEST_1 | ERROR] Cannot establish connection with test server: " + e.getMessage());
        }
    }

    @Test
    @Tag("TEST_2")
    public void testServerSubscription() {
        try (Socket socket = new Socket(testIp, testPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String greeting = reader.readLine();
            log.debug("[TEST_2 | DEBUG] greeting is {}", greeting);
            assertNotNull(greeting, "Сервер должен отправить приветственное сообщение");

            writer.println("SUB:system/metrics");

            String response = reader.readLine();
            log.debug("[TEST_2 | DEBUG] Response is {}", response);

            assertNotNull(response, "Server must answer to SUBSCRIBE");
            assertTrue(response.contains("SUBSCRIBED_OK system/metrics"), "Server must confirm subscription");

        } catch (Exception e) {
            fail("[TEST_2 | DEBUG] Cannot set subscriber by topic: " + e.getMessage());
        }
    }

    @Test
    @Tag("TEST_3")
    public void testServerPublisherWithValidJsonPayload() {
        try (Socket socket = new Socket(testIp, testPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String greeting = reader.readLine();
            log.debug("[TEST_3 | DEBUG] greeting is {}", greeting);

            MessageDTO messageDto = new MessageDTO("TestClient", "Hello world JSON", System.currentTimeMillis());
            String jsonPayload = objectMapper.writeValueAsString(messageDto);

            writer.println("PUB:news:" + jsonPayload);

            String response = reader.readLine();
            log.debug("[TEST_3 | DEBUG] Response is {}", response);

            assertNotNull(response, "Ответ сервера не должен быть null");
            assertTrue(response.contains("PUBLISHED_OK"), "Server must confirm (PUBLISHED_OK)");

        } catch (Exception e) {
            fail("Publisher test failed: " + e.getMessage());
        }
    }

    @Test
    @Tag("TEST_4")
    public void testServerPublisherRejectsInvalidJson() {
        try (Socket socket = new Socket(testIp, testPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            reader.readLine();

            writer.println("PUB:news:NOT_A_VALID_JSON");

            String response = reader.readLine();
            log.debug("[TEST_4 | DEBUG] Response is {}", response);

            assertNotNull(response, "Ответ сервера не должен быть null");
            assertTrue(response.contains("ERROR: Invalid JSON structure"), "Server must return JSON error");

        } catch (Exception e) {
            fail("Invalid JSON test failed: " + e.getMessage());
        }
    }
}
