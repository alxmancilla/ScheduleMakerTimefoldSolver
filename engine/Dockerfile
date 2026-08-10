# Multi-stage build for the scheduling engine worker.
#
# The engine is a one-shot batch job: it loads the problem from PostgreSQL, runs
# the Timefold solver, writes the solution back to the database, and emits PDF
# reports to /work. Run it on demand or on a schedule (cron / ECS scheduled task
# / Kubernetes CronJob). Results surface through the web API, which reads the
# same database.
#
# Build (from the repository root, so the parent POM is in context):
#   docker build -f engine/Dockerfile -t scheduler-engine .
#
# Run once against your database:
#   docker run --rm \
#     -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
#     -e DB_USER=<user> -e DB_PASSWORD=<pass> \
#     -v "$PWD/reports:/work" \
#     scheduler-engine

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Copy the reactor: parent POM + engine module sources. The web module is not
# needed to build the engine, so only bring in what the engine build requires.
COPY pom.xml ./
COPY engine/pom.xml ./engine/pom.xml
COPY engine/src ./engine/src

# Build only the engine module (and its parent) into the shaded fat jar. The
# glob matches the shaded artifact only (the shade "original-" jar has a
# different prefix).
RUN mvn -q -pl engine -am -DskipTests package \
    && cp engine/target/scheduler-engine-*.jar /engine.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /work

# Non-root runtime user.
RUN useradd --system --create-home --uid 10001 worker
COPY --from=build /engine.jar /app/engine.jar
RUN chown -R worker:worker /work /app
USER worker

# Default DB connection; override at run time.
ENV DB_URL=jdbc:postgresql://localhost:5432/school_schedule \
    DB_USER=mancilla \
    DB_PASSWORD="" \
    JAVA_OPTS=""

# PDF reports are written to the working directory (/work); mount a volume there
# to retain them. The DB write is the primary, durable output.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/engine.jar"]
