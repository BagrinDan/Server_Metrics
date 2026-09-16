package main

import (
	"bufio"
	"bytes"
	"fmt"
	"log"
	"net"
	"net/http"
	"strings"
	"time"
)

func sendToLoki(jsonPayload string) {
	url := "http://localhost:8091/loki/api/v1/push"
	timestamp := fmt.Sprintf("%d", time.Now().UnixNano())

	payload := fmt.Sprintf(`{
		"streams": [
			{
				"stream": {
					"job": "server_metrics_logs",
					"source": "sub2"
				},
				"values": [
					["%s", "%s"]
				]
			}
		]
	}`, timestamp, jsonPayload)

	resp, err := http.Post(url, "application/json", bytes.NewBuffer([]byte(payload)))
	if err != nil {
		log.Printf("Ошибка отправки в Loki: %v\n", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusNoContent && resp.StatusCode != http.StatusOK {
		log.Printf("Loki вернул неожиданный статус: %d\n", resp.StatusCode)
	}
}

func main() {
	var conn net.Conn
	var err error

	for {
		conn, err = net.Dial("tcp", "localhost:8080")
		if err == nil {
			break
		}
		log.Println("Не удалось подключиться к брокеру, повтор через 2 секунды...")
		time.Sleep(2 * time.Second)
	}
	defer conn.Close()
	fmt.Println("Успешно подключено к брокеру!")

	reader := bufio.NewReader(conn)
	welcomeMsg, _ := reader.ReadString('\n')
	fmt.Print("Брокер ответил: ", welcomeMsg)

	subCommand := "SUB:logs\n"
	_, err = conn.Write([]byte(subCommand))
	if err != nil {
		log.Fatalf("Ошибка отправки подписки: %v", err)
	}

	fmt.Println("Sub 2 (Logs) подписался на топик 'logs' и слушает данные...")

	for {
		message, err := reader.ReadString('\n')
		if err != nil {
			log.Println("Соединение с брокером разорвано:", err)
			break
		}

		message = strings.TrimSpace(message)
		if message == "" {
			continue
		}

		if strings.HasPrefix(message, "MSG ") {
			parts := strings.SplitN(message, " ", 3)
			if len(parts) == 3 {
				topic := parts[1]
				jsonPayload := parts[2]

				fmt.Printf("[LOGS -> LOKI] Топик: [%s] Данные: %s\n", topic, jsonPayload)

				go sendToLoki(jsonPayload)
			} else {
				fmt.Println("Получено странное сообщение от брокера:", message)
			}
		} else {
			fmt.Println("Системное сообщение:", message)
		}
	}
}