# Multi-stage build for the PDF reporter worker.
#
# The reporter is a one-shot batch job: it reconstructs the solved schedule from
# PostgreSQL (the rows the engine persists), recomputes constraint violations,
# and writes the three PDF reports to /work. Run it on demand or right after the
# engine (cron / ECS scheduled task / Kubernetes CronJob). It shares only the
# database with the engine and web modules.
#
# Build (from the repository root, so the parent POM is in context):
#   docker build -f reporter/Dockerfile -t scheduler-reporter .
#
# Run once against your database:
#   docker run --rm \
#     -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
#     -e DB_USER=<user> -e DB_PASSWORD=<pass> \
#     -v "$PWD/reports:/work" \
#     scheduler-reporter

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Copy the reactor: the parent POM lists all three modules, so Maven must be able
# to read every module POM to resolve the reactor. Bring in all module POMs, plus
# the sources for the reporter and its library dependency (engine). The web
# module is only read, not built, so its sources are not needed.
COPY pom.xml ./
COPY engine/pom.xml ./engine/pom.xml
COPY reporter/pom.xml ./reporter/pom.xml
COPY web/pom.xml ./web/pom.xml
COPY engine/src ./engine/src
COPY reporter/src ./reporter/src

# Build the reporter module (and everything it needs: parent + engine) into the
# shaded fat jar. The glob matches the shaded artifact only (the shade
# "original-" jar has a different prefix).
RUN mvn -q -pl reporter -am -DskipTests package \
    && cp reporter/target/scheduler-reporter-*.jar /reporter.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /work

# Non-root runtime user.
RUN useradd --system --create-home --uid 10001 worker
COPY --from=build /reporter.jar /app/reporter.jar
RUN chown -R worker:worker /work /app
USER worker

# Default DB connection; override at run time. JAVA_OPTS sets a deterministic
# heap ceiling relative to the container memory limit (via -XX:MaxRAMPercentage),
# so the JVM sizes itself off the container's cgroup rather than the whole host.
# Pair with `docker run --memory=<limit>` for a deterministic heap.
ENV DB_URL=jdbc:postgresql://localhost:5432/school_schedule \
    DB_USER=mancilla \
    DB_PASSWORD="" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# PDF reports are written to the working directory (/work); mount a volume there
# to retain them.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/reporter.jar"]
