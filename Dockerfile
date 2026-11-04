FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY core/pom.xml core/pom.xml
COPY examples/pom.xml examples/pom.xml
COPY core/src core/src
COPY examples/src examples/src
RUN mvn -q -B clean verify

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app
COPY --from=build /workspace/core/target/classes ./core
COPY --from=build /workspace/examples/target/classes ./examples
ENTRYPOINT ["java", "-cp", "/app/examples:/app/core", "dev.notifications.demo.Demo"]
