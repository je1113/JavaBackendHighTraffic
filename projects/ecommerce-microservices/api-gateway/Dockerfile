FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the built JAR file
COPY build/libs/api-gateway-*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]