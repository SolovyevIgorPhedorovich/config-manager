# ---------- Этап 1: сборка fat-jar через Gradle ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Сначала только файлы сборки — слой кэшируется, пока не меняются зависимости
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

# Затем исходники и сборка (тесты прогоняются в CI, в образе пропускаем)
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- Этап 2: рантайм-образ только с JRE ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Непривилегированный пользователь
RUN useradd -r -u 1001 -m appuser
COPY --from=build /app/build/libs/configmanager-*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
