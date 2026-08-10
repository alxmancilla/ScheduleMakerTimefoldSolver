# Multi-stage build for the scheduling engine worker.
#
# The engine is a one-shot batch job: it loads the problem from PostgreSQL, runs
# the Timefold solver, and writes the solution back to the database. Run it on
# demand or on a schedule (cron / ECS scheduled task / Kubernetes CronJob).
# Results surface through the web API, which reads the same database. PDF reports
# are produced separately by the reporter image.
#
# Build (from the repository root, so the parent POM is in context):
#   docker build -f engine/Dockerfile -t scheduler-engine .
#
# Run once against your database. The solver is CPU- and memory-hungry, so give
# it more than the other images: at least 4 CPUs / 8 GB, at most 6 CPUs / 12 GB.
# In plain `docker run`, --cpus/--memory set the ceiling and --memory-reservation
# sets the memory floor (a CPU floor needs an orchestrator: K8s requests, Compose
# reservations):
#   docker run --rm \
#     --cpus=6 --memory=12g --memory-reservation=8g \
#     -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
#     -e DB_USER=<user> -e DB_PASSWORD=<pass> \
#     scheduler-engine
#
# JAVA_OPTS (below) sizes the heap to 75% of the container memory limit, so the
# heap tracks --memory deterministically (~6 GB at 8g, ~9 GB at 12g).

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Copy the reactor: the parent POM lists all three modules, so Maven must be able
# to read every module POM to resolve the reactor (even when building only the
# engine). Bring in all module POMs, but only the engine sources are needed.
COPY pom.xml ./
COPY engine/pom.xml ./engine/pom.xml
COPY reporter/pom.xml ./reporter/pom.xml
COPY web/pom.xml ./web/pom.xml
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

# Default DB connection; override at run time. JAVA_OPTS sets a deterministic
# heap ceiling relative to the container memory limit (via -XX:MaxRAMPercentage),
# so the solver's heap tracks the container's cgroup rather than the whole host.
# Pair with `docker run --memory=<limit>` (see the recommended range above).
ENV DB_URL=jdbc:postgresql://localhost:5432/school_schedule \
    DB_USER=mancilla \
    DB_PASSWORD="" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# The solved schedule is written to the database (the primary, durable output);
# generate PDFs afterwards with the reporter image.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/engine.jar"]
