FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/be-storytellers-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
