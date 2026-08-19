FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/payment-service/build/libs/payment-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
