# Этап 1: сборка jar. Образ с Maven и полным JDK нужен только здесь
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Сначала только pom.xml: пока он не меняется, Docker берёт скачанные зависимости из кэша
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Тесты здесь не запускаются: им нужен Docker (Testcontainers). Запуск тестов: mvn test
RUN mvn -B package -DskipTests

# Этап 2: запуск. В итоговый образ попадают только JRE и jar
FROM eclipse-temurin:21-jre
WORKDIR /app

# Не root: если приложение взломают, у атакующего не будет прав root в контейнере
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
