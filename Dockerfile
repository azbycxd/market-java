# syntax=docker/dockerfile:1

FROM maven:3.8.8-eclipse-temurin-8 AS build

WORKDIR /workspace

# Build the complete Maven reactor inside the image; no host Maven/JDK or prebuilt jar is used.
COPY pom.xml ./
COPY group-buy-market-api/pom.xml group-buy-market-api/pom.xml
COPY group-buy-market-app/pom.xml group-buy-market-app/pom.xml
COPY group-buy-market-domain/pom.xml group-buy-market-domain/pom.xml
COPY group-buy-market-infrastructure/pom.xml group-buy-market-infrastructure/pom.xml
COPY group-buy-market-trigger/pom.xml group-buy-market-trigger/pom.xml
COPY group-buy-market-types/pom.xml group-buy-market-types/pom.xml
COPY group-buy-market-api/src group-buy-market-api/src
COPY group-buy-market-app/src group-buy-market-app/src
COPY group-buy-market-domain/src group-buy-market-domain/src
COPY group-buy-market-infrastructure/src group-buy-market-infrastructure/src
COPY group-buy-market-trigger/src group-buy-market-trigger/src
COPY group-buy-market-types/src group-buy-market-types/src

RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:8-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/data/log \
    && chown -R app:app /app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/group-buy-market-app/target/group-buy-market-app.jar /app/group-buy-market-app.jar

ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m"

EXPOSE 8091

USER app

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD curl --fail --silent --show-error http://127.0.0.1:${SERVER_PORT:-8091}/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/group-buy-market-app.jar"]
