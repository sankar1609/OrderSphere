# Build context must be the repository root, e.g.:
#   docker build -f services/inventory-service/Dockerfile -t ordersphere/inventory-service .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/common-events/pom.xml services/common-events/pom.xml
COPY services/common-security/pom.xml services/common-security/pom.xml
COPY services/service-registry/pom.xml services/service-registry/pom.xml
COPY services/ordersphere-gateway/pom.xml services/ordersphere-gateway/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/inventory-service/pom.xml services/inventory-service/pom.xml
COPY services/common-events/src services/common-events/src
COPY services/common-security/src services/common-security/src
COPY services/inventory-service/src services/inventory-service/src
RUN mvn -pl services/inventory-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/target/inventory-service.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
