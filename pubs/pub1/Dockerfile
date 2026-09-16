# Этап сборки
FROM golang:alpine AS builder
WORKDIR /app
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o pub1-app .

# Финальный легковесный образ
FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/pub1-app .

EXPOSE 8080
CMD ["./pub1-app"]
