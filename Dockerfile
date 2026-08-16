# Stage 1: Build the application using Maven
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and configuration files first to cache dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Copy the actual source code and build the jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application using a lightweight JRE image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]