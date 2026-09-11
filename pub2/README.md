# Log publisher

`pub2` generates structured worker log events and sends one JSON event per
interval to the broker over TCP. Each connection contains one newline-terminated
JSON object.

Environment variables:

- `BROKER_HOST` (default `127.0.0.1`)
- `BROKER_PORT` (default `8080`)
- `PUBLISH_INTERVAL_SECONDS` (default `5`)
- `LOG_SERVICE` (default `telemetry-worker`)

Run with `python publisher.py` from this directory.
