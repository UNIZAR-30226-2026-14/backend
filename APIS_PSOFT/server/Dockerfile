
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY APIS_PSOFT/server/pom.xml .
COPY APIS_PSOFT/server/src ./src

RUN mvn --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/target/server-0.0.1-SNAPSHOT.jar app.jar

COPY public /app/public

ENV SERVER_SSL_ENABLED=false
ENV STATIC_PUBLIC_PATH=/app/public/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
