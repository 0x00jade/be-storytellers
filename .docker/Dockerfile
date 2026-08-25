# Stage 1: build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Pin Gradle cache inside the build context so dependency layers are reused
# when only src/ changes
ENV GRADLE_USER_HOME=/build/.gradle

COPY ../gradlew gradlew.bat ./
COPY ../gradle gradle/
RUN chmod +x gradlew

# Resolve dependencies before copying source — maximises layer cache hits
COPY ../build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY ../src src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/build/libs/be-storytellers-*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
