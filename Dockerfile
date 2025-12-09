# 1) Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom + src
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src ./src

# Build jar
RUN mvn -q -DskipTests package

# 2) Runtime stage (lighter image)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Spring Boot defaults to 8080
EXPOSE 8080

# Use efficient JVM flags for container
ENTRYPOINT ["java", "-XX:+UseZGC", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
