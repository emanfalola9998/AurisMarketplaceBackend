# ─── Dockerfile ────────────────────────────────────────────────────────────
#
# Multi-stage build: compile and stage the Play app with sbt, then copy just
# the staged runtime into a slim JRE image. Used for deploying to Render
# (see render.yaml) — not needed for local dev, which runs via `sbt run`.

FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

RUN apt-get update && apt-get install -y curl gnupg && \
    curl -fL https://github.com/sbt/sbt/releases/download/v1.12.9/sbt-1.12.9.tgz | tar xz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt && \
    rm -rf /var/lib/apt/lists/*

COPY project project
COPY build.sbt .
RUN sbt update

COPY app app
COPY conf conf
RUN sbt stage

FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app
COPY --from=build /app/target/universal/stage /app

EXPOSE 10000
# Render sets $PORT at runtime; shell form so it's substituted (exec form can't).
ENTRYPOINT /app/bin/auris -Dhttp.port=${PORT:-10000} -Dpidfile.path=/dev/null
