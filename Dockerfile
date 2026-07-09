# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the Maven descriptors first so dependency resolution can be cached.
COPY pom.xml .
COPY notifications-core/pom.xml notifications-core/pom.xml
COPY notifications-application/pom.xml notifications-application/pom.xml
COPY notifications-adapters/pom.xml notifications-adapters/pom.xml
COPY notifications-observability-otel/pom.xml notifications-observability-otel/pom.xml
COPY notifications-demo/pom.xml notifications-demo/pom.xml

# Source tree used by the shared-module layout.
COPY src src

RUN mvn -pl notifications-demo -am -Pdemo-executable -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/notifications-demo/target/notifications-demo-1.0.0.jar /app/notifications-demo.jar

ENV MAIN_CLASS=com.demo.notifications.examples.NotificationOptimizedMain

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -cp /app/notifications-demo.jar ${MAIN_CLASS:-com.demo.notifications.examples.NotificationOptimizedMain} \"$@\"", "--"]
