# ---- Этап сборки ----
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
# Копируем файлы для загрузки зависимостей
COPY pom.xml .
RUN mvn dependency:go-offline
# Копируем исходный код и собираем приложение
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Этап выполнения ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем собранный jar-файл из этапа сборки
COPY --from=build /app/target/*.jar app.jar
# Указываем порт, который будет слушать приложение
EXPOSE 8080
# Команда для запуска приложения
ENTRYPOINT ["java", "-jar", "app.jar"]