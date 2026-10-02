# Platforma SOS – demo portál: Angular (build) -> Spring Boot (jar) -> runtime
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist/portal/browser/ ./src/main/resources/static/
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/target/portal-demo.jar /app/portal-demo.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/portal-demo.jar"]
