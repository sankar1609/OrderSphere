# Build context must be the repository root, e.g.:
#   docker build -f services/dummy-payment-gateway/Dockerfile -t ordersphere/dummy-payment-gateway .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/common-events/pom.xml services/common-events/pom.xml
COPY services/common-security/pom.xml services/common-security/pom.xml
COPY services/service-registry/pom.xml services/service-registry/pom.xml
COPY services/ordersphere-gateway/pom.xml services/ordersphere-gateway/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/inventory-service/pom.xml services/inventory-service/pom.xml
COPY services/ordersphere-orders/pom.xml services/ordersphere-orders/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/shipping-service/pom.xml services/shipping-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/dummy-payment-gateway/pom.xml services/dummy-payment-gateway/pom.xml
COPY services/dummy-payment-gateway/src services/dummy-payment-gateway/src
RUN mvn -pl services/dummy-payment-gateway -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/dummy-payment-gateway/target/dummy-payment-gateway.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
