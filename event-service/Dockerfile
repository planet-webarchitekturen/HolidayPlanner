# Build from root: docker build -f event-service/Dockerfile .
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY booklet-service/pom.xml booklet-service/pom.xml
COPY booklet-request-service/pom.xml booklet-request-service/pom.xml
COPY event-service/pom.xml event-service/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY organization-service/pom.xml organization-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY shared/src shared/src
COPY event-service/src event-service/src
RUN mvn install -N -DskipTests && \
    mvn install -pl shared -DskipTests && \
    mvn package -pl event-service -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/event-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
