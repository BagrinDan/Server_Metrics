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

// Функция для отправки метрики в InfluxDB (используем Line Protocol)
func sendToInfluxDB(jsonPayload string) {
	// Эндпоинт InfluxDB v2 для записи (Line Protocol)
	// При необходимости укажите свою организацию, бакет и токен
	url := "http://localhost:8086/api/v2/write?org=my-org&bucket=my-bucket&precision=ns"
	token := "your-influxdb-token" // Замените на ваш токен авторизации InfluxDB

	// Формируем Line Protocol для InfluxDB.
	// Пример измерения: system_metrics,source=sub1 cpu_usage=45.2,ram_usage=1024 <timestamp>
	// Для универсальности передаем JSON или парсим его. Здесь шлем как поле string или value.
	// Простейший вариант отправки сырого json-эквивалента или метрики:
	timestamp := time.Now().UnixNano()
	
	// Строка в формате InfluxDB Line Protocol:
	// measurement,tag=value field=value timestamp
	lineProtocol := fmt.Sprintf("server_metrics,source=sub1 raw_data=\"%s\" %d", strings.ReplaceAll(jsonPayload, "\"", "\\\""), timestamp)

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

	// Цикл подключения с реконнектом к Java-брокеру
	for {
		conn, err = net.Dial("tcp", "localhost:8080")
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

// Подписываемся на топик метрик (например, "metrics" или "system/metrics")
	subCommand := "SUB:metrics\n"
	_, err = conn.Write([]byte(subCommand))
	if err != nil {
		log.Fatalf("Sub 1: Ошибка отправки подписки: %v", err)
	}

	fmt.Println("Sub 1 (Metrics) подписался на топик 'metrics' и слушает данные...")

	// Бесконечно читаем поток данных от брокера
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

		// Обрабатываем формат брокера: "MSG <topic> <payload>"
		if strings.HasPrefix(message, "MSG ") {
			parts := strings.SplitN(message, " ", 3)
			if len(parts) == 3 {
				topic := parts[1]
				jsonPayload := parts[2]

				fmt.Printf("[METRICS -> INFLUX] Топик: [%s] Данные: %s\n", topic, jsonPayload)

				// Асинхронно отправляем в InfluxDB
				go sendToInfluxDB(jsonPayload)
			} else {
				fmt.Println("Sub 1: Получено странное сообщение:", message)
			}
		} else {
			fmt.Println("Sub 1 Системное сообщение:", message)
		}
	}
}