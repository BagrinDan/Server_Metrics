# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# --- runtime stage ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/app.jar app.jar

EXPOSE 9092

ENTRYPOINT ["java", "-jar", "app.jar"]