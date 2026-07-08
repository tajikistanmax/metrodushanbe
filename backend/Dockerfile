# Backend «Метро Душанбе» — multi-stage образ.
# Сборка через Maven Wrapper на Temurin JDK 25 → рантайм на Temurin JRE 25.
# Контекст сборки — каталог backend/ (см. backend/.dockerignore):
#   docker build -f backend/Dockerfile -t metro-backend backend

# ---- Stage 1: сборка fat-jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# maven-wrapper.jar в репозитории gitignored (нет в build-контексте), а mvnw
# докачивает его сам через curl/wget — которых нет в базовом Temurin-образе.
# Ставим curl, чтобы ./mvnw смог подтянуть wrapper и дистрибутив Maven.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Сначала только Maven Wrapper + pom.xml — прогреваем кэш зависимостей отдельным
# слоем, чтобы правки в src/ не тянули повторную загрузку всего репозитория.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline || true

# Теперь исходники и сборка. Тесты пропускаем (их гоняет CI с Testcontainers).
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests clean package

# ---- Stage 2: минимальный рантайм ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Непривилегированный пользователь (не запускаем JVM из-под root).
RUN groupadd --system spring && useradd --system --gid spring --home /app spring
USER spring:spring

# Единственный repackage-jar Spring Boot (metro-backend-*.jar; *.jar.original не
# попадает под маску *.jar).
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

EXPOSE 8080

# Слушать все интерфейсы контейнера; порт/контекст берутся из application.yml.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
