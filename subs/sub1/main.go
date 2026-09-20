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
	"os"
	"encoding/json"
)

type PubPayload struct {
    Payload struct {
        CPUPercent       float64 `json:"cpu_percent"`
        MemoryUsedBytes  float64 `json:"memory_used_bytes"`
        MemoryTotalBytes float64 `json:"memory_total_bytes"`
        MemoryPercent    float64 `json:"memory_percent"`
        Host             string  `json:"host"`
    } `json:"payload"`
}


func sendToInfluxDB(jsonPayload string) {
    var parsed PubPayload
    if err := json.Unmarshal([]byte(jsonPayload), &parsed); err != nil {
        log.Printf("Ошибка парсинга payload: %v\n", err)
        return
    }

    influxURL := envOr("INFLUXDB_URL", "CANT READ ENV")
    url := fmt.Sprintf("%s/api/v2/write?org=lab1&bucket=metrics&precision=ns", influxURL)
    token := envOr("INFLUXDB_TOKEN", "IS_NIHIL")

    timestamp := time.Now().UnixNano()
    lineProtocol := fmt.Sprintf(
        "server_metrics,source=sub1,host=%s cpu_percent=%f,memory_used_bytes=%f,memory_total_bytes=%f,memory_percent=%f %d",
        parsed.Payload.Host,
        parsed.Payload.CPUPercent,
        parsed.Payload.MemoryUsedBytes,
        parsed.Payload.MemoryTotalBytes,
        parsed.Payload.MemoryPercent,
        timestamp,
    )

    req, err := http.NewRequest("POST", url, bytes.NewBuffer([]byte(lineProtocol)))
    if err != nil {
        log.Printf("Ошибка создания запроса в InfluxDB: %v\n", err)
        return
    }

    req.Header.Set("Authorization", "Token "+token)
    req.Header.Set("Content-Type", "text/plain; charset=utf-8")

    client := &http.Client{Timeout: 5 * time.Second}
    resp, err := client.Do(req)
    if err != nil {
        log.Printf("Ошибка отправки в InfluxDB: %v\n", err)
        return
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusNoContent && resp.StatusCode != http.StatusOK {
        log.Printf("InfluxDB вернул неожиданный статус: %d\n", resp.StatusCode)
    }
}

func main() {
	var conn net.Conn
	var err error

	host := envOr("BROKER_HOST", "CANT_READ_FLICKING_ENV")
	port := envOr("BROKER_PORT", "CANT_READ_FLICKING_ENV")

	log.Printf("Sub1 is trying to knock-knock to: %s %s",host, port)

	for {
		conn, err = net.Dial("tcp", host+":"+port)
		
		if err == nil {
			break
		}
		log.Println("Sub 1: Не удалось подключиться к брокеру, повтор через 2 секунды...")
		time.Sleep(2 * time.Second)
	}
	defer conn.Close()
	fmt.Println("Sub 1: Успешно подключено к брокеру!")

	reader := bufio.NewReader(conn)
	welcomeMsg, _ := reader.ReadString('\n')
	fmt.Print("Брокер ответил: ", welcomeMsg)

	subCommand := "SUB:metrics\n"
	_, err = conn.Write([]byte(subCommand))
	if err != nil {
		log.Fatalf("Sub 1: Ошибка отправки подписки: %v", err)
	}

	fmt.Println("Sub 1 (Metrics) подписался на топик 'metrics' и слушает данные...")


	for {
		message, err := reader.ReadString('\n')
		if err != nil {
			log.Println("Sub 1: Соединение с брокером разорвано:", err)
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

				fmt.Printf("[METRICS -> INFLUX] Топик: [%s] Данные: %s\n", topic, jsonPayload)


				go sendToInfluxDB(jsonPayload)
			} else {
				fmt.Println("Sub 1: Получено странное сообщение:", message)
			}
		} else {
			fmt.Println("Sub 1 Системное сообщение:", message)
		}
	}
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}