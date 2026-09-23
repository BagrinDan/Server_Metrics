# Metrics publisher

`pub1` samples CPU and memory usage and sends one JSON event per interval to the
broker over TCP. Each connection contains one newline-terminated JSON object.

Environment variables:

- `BROKER_HOST` (default `127.0.0.1`)
- `BROKER_PORT` (default `9000`)
- `PUBLISH_INTERVAL_SECONDS` (default `5`)

Run with `go run .` from this directory.
