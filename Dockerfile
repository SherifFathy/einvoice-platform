# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent POM and all module POMs first to allow Docker layer-caching of
# the dependency download step (invalidated only when a POM changes).
COPY pom.xml ./
COPY platform-core/pom.xml    platform-core/
COPY platform-security/pom.xml platform-security/
COPY platform-eta/pom.xml     platform-eta/
COPY platform-zatca/pom.xml   platform-zatca/
COPY platform-pdf/pom.xml     platform-pdf/
COPY platform-jobs/pom.xml    platform-jobs/
COPY platform-api/pom.xml     platform-api/

RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source after the dependency cache is warm.
COPY platform-core/src     platform-core/src
COPY platform-security/src platform-security/src
COPY platform-eta/src      platform-eta/src
COPY platform-zatca/src    platform-zatca/src
COPY platform-pdf/src      platform-pdf/src
COPY platform-jobs/src     platform-jobs/src
COPY platform-api/src      platform-api/src

RUN mvn -pl platform-api -am package -DskipTests -Dcheckstyle.skip -B --no-transfer-progress

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN addgroup --system einvoice && adduser --system --ingroup einvoice einvoice
USER einvoice

COPY --from=build /app/platform-api/target/platform-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
