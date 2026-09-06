package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/host"
	"github.com/shirou/gopsutil/v3/mem"
)

type config struct {
	BrokerHost string
	BrokerPort string
	Interval   time.Duration
}

type event struct {
	Topic     string      `json:"topic"`
	Timestamp string      `json:"timestamp"`
	Publisher string      `json:"publisher"`
	Payload   interface{} `json:"payload"`
}

type metrics struct {
	CPUPercent    float64 `json:"cpu_percent"`
	MemoryUsed    uint64  `json:"memory_used_bytes"`
	MemoryTotal   uint64  `json:"memory_total_bytes"`
	MemoryPercent float64 `json:"memory_percent"`
	Host          string  `json:"host"`
}

func main() {
	cfg, err := loadConfig()
	if err != nil {
		log.Fatal(err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	log.Printf("metrics publisher starting; broker=%s:%s interval=%s", cfg.BrokerHost, cfg.BrokerPort, cfg.Interval)
	for {
		select {
		case <-ctx.Done():
			log.Print("metrics publisher stopped")
			return
		default:
		}

		current, err := collectMetrics()
		if err != nil {
			log.Printf("collecting metrics: %v", err)
		} else {
			if err := publish(cfg, event{
				Topic:     "metrics",
				Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
				Publisher: "pub1",
				Payload:   current,
			}); err != nil {
				log.Printf("publishing metrics: %v", err)
			}
		}

		timer := time.NewTimer(cfg.Interval)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}

func loadConfig() (config, error) {
	host := envOr("BROKER_HOST", "127.0.0.1")
	port := envOr("BROKER_PORT", "9000")
	seconds, err := strconv.Atoi(envOr("PUBLISH_INTERVAL_SECONDS", "5"))
	if err != nil || seconds <= 0 {
		return config{}, fmt.Errorf("PUBLISH_INTERVAL_SECONDS must be a positive integer")
	}
	return config{BrokerHost: host, BrokerPort: port, Interval: time.Duration(seconds) * time.Second}, nil
}

func collectMetrics() (metrics, error) {
	values, err := cpu.Percent(time.Second, false)
	if err != nil {
		return metrics{}, err
	}
	cpuPercent := first(values)
	memory, err := mem.VirtualMemory()
	if err != nil {
		return metrics{}, err
	}
	hostName, err := host.Info()
	if err != nil {
		return metrics{}, err
	}
	return metrics{
		CPUPercent:    cpuPercent,
		MemoryUsed:    memory.Used,
		MemoryTotal:   memory.Total,
		MemoryPercent: memory.UsedPercent,
		Host:          hostName.Hostname,
	}, nil
}

func publish(cfg config, message event) error {
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(cfg.BrokerHost, cfg.BrokerPort), 3*time.Second)
	if err != nil {
		return err
	}
	defer conn.Close()
	if err := conn.SetWriteDeadline(time.Now().Add(3 * time.Second)); err != nil {
		return err
	}
	writer := bufio.NewWriter(conn)
	if err := json.NewEncoder(writer).Encode(message); err != nil {
		return err
	}
	return writer.Flush()
}

func first(values []float64) float64 {
	if len(values) == 0 {
		return 0
	}
	return values[0]
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
