FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

ARG OTEL_AGENT_VERSION=2.27.0
ARG OTEL_AGENT_SHA256=bd01fea1304e8c8803fff827a0bdda02b2266742a85c62548053c6761474bb5b
RUN curl -fsSL -o /otel-agent.jar \
      "https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar" \
 && echo "${OTEL_AGENT_SHA256}  /otel-agent.jar" | sha256sum -c -

COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --version --no-daemon

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon \
 && cp build/libs/*.jar /app.jar


FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --system ledger && useradd --system --gid ledger ledger
COPY --from=build --chown=ledger:ledger /app.jar /app/app.jar
COPY --from=build --chown=ledger:ledger /otel-agent.jar /app/otel-agent.jar
USER ledger

EXPOSE 8080
# Agent is inert unless JAVA_TOOL_OPTIONS=-javaagent:/app/otel-agent.jar is set.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
