# Build context must be the repository root, e.g.:
#   docker build -f services/service-registry/Dockerfile -t ordersphere/service-registry .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/common-events/pom.xml services/common-events/pom.xml
COPY services/common-security/pom.xml services/common-security/pom.xml
COPY services/service-registry/pom.xml services/service-registry/pom.xml
COPY services/ordersphere-gateway/pom.xml services/ordersphere-gateway/pom.xml
COPY services/common-events/src services/common-events/src
COPY services/common-security/src services/common-security/src
COPY services/service-registry/src services/service-registry/src
RUN mvn -pl services/service-registry -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/service-registry/target/service-registry.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
