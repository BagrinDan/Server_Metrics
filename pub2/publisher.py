"""Generate and publish structured log events over a JSON-lines TCP connection."""

from __future__ import annotations

import json
import logging
import os
import random
import socket
import time
from datetime import datetime, timezone


def env_int(name: str, default: int) -> int:
    value = os.getenv(name, str(default))
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer") from exc
    if parsed <= 0:
        raise ValueError(f"{name} must be positive")
    return parsed


def make_log_event() -> dict:
    level = random.choices(
        ["INFO", "WARNING", "ERROR"], weights=[80, 15, 5], k=1
    )[0]
    messages = {
        "INFO": "worker heartbeat received",
        "WARNING": "worker response exceeded expected latency",
        "ERROR": "worker failed to process event",
    }
    return {
        "topic": "logs",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "publisher": "pub2",
        "payload": {
            "level": level,
            "message": messages[level],
            "service": os.getenv("LOG_SERVICE", "telemetry-worker"),
            "sequence": int(time.time_ns()),
        },
    }


def publish(host: str, port: int, event: dict) -> None:
    encoded = (json.dumps(event, separators=(",", ":")) + "\n").encode("utf-8")
    with socket.create_connection((host, port), timeout=3) as connection:
        connection.sendall(encoded)


def main() -> None:
    host = os.getenv("BROKER_HOST", "127.0.0.1")
    port = env_int("BROKER_PORT", 9000)
    interval = env_int("PUBLISH_INTERVAL_SECONDS", 5)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    logging.info("log publisher starting; broker=%s:%s interval=%ss", host, port, interval)

    while True:
        event = make_log_event()
        try:
            publish(host, port, event)
            logging.info("published %s log event", event["payload"]["level"])
        except OSError as exc:
            logging.error("publishing log event: %s", exc)
        time.sleep(interval)


if __name__ == "__main__":
    main()
