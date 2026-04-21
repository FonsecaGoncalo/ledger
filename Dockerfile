FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

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
USER ledger

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
