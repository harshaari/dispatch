FROM eclipse-temurin:21-jre

WORKDIR /app
COPY services/order-service/build/libs/order-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
