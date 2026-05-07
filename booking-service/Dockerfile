# Build from root: docker build -f booking-service/Dockerfile .
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# All module pom.xml files must be present so the root reactor can resolve every
# <module> entry in the root pom.xml, even though we only compile a subset.
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
# Only copy source for the modules we actually compile
COPY shared/src shared/src
COPY booking-service/src booking-service/src
RUN mvn install -N -DskipTests && \
    mvn install -pl shared -DskipTests && \
    mvn package -pl booking-service -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/booking-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
