# Multi-stage build for the web module (Spring Boot REST API).
#
# Unlike the engine and reporter (one-shot batch workers), the web module is a
# long-running service: it serves the REST API on port 8080, backed by the same
# PostgreSQL database. The React frontend in web-ui/ talks to it.
#
# Build (from the repository root, so the parent POM is in context):
#   docker build -f web/Dockerfile -t scheduler-web .
#
# Run with deterministic resource limits (recommended):
#   docker run --rm \
#     --cpus=2 --memory=1g \
#     -p 8080:8080 \
#     -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
#     -e DB_USER=<user> -e DB_PASSWORD=<pass> \
#     -e CORS_ALLOWED_ORIGINS=https://app.example.com \
#     scheduler-web
#
# The JVM heap auto-sizes to a fraction of the container's memory limit via
# -XX:MaxRAMPercentage (see JAVA_OPTS below), so setting --memory alone gives a
# deterministic heap ceiling. Override JAVA_OPTS to tune further.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Copy the reactor: the parent POM lists all three modules, so Maven must be able
# to read every module POM to resolve the reactor. Bring in all module POMs, plus
# the sources for the web module and its build dependency (engine is not required
# by web, so only the web sources are needed).
COPY pom.xml ./
COPY engine/pom.xml ./engine/pom.xml
COPY reporter/pom.xml ./reporter/pom.xml
COPY web/pom.xml ./web/pom.xml
COPY web/src ./web/src

# Build only the web module (and its parent) into the Spring Boot executable jar.
# The glob matches the repackaged artifact (the plain "original-" jar has a
# different prefix and is left behind by spring-boot:repackage).
RUN mvn -q -pl web -am -DskipTests package \
    && cp web/target/scheduler-web-*.jar /web.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root runtime user.
RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build /web.jar /app/web.jar
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# Default config; override at run time. JAVA_OPTS sets a deterministic heap
# ceiling relative to the container memory limit, so the JVM does not size itself
# off the whole host.
ENV DB_URL=jdbc:postgresql://localhost:5432/school_schedule \
    DB_USER=mancilla \
    DB_PASSWORD="" \
    CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:5173" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/web.jar"]
