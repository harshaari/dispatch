FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/dispatch-service/build/libs/dispatch-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
