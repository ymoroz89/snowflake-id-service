# snowflake-id-service

Distributed ID generation service based on the Snowflake algorithm, exposed via gRPC and packaged as a multi-module Gradle project.

## What This Project Provides

- A gRPC server that generates 64-bit unique IDs.
- Shared protobuf contracts for server and clients.
- A reusable Java client library with Spring Boot auto-configuration.
- Container and Helm artifacts for deployment.

## Project Modules

- `snowflake-server`
  - Spring Boot gRPC service implementation.
  - Contains the Snowflake generation logic and gRPC endpoint implementation.
  - Entry point: `SnowflakeIdServiceApplication`.

- `snowflake-client`
  - Java client library for calling the gRPC service.
  - Includes `SnowflakeClient` for manual use and Spring Boot auto-configuration.
  - Useful for integrating ID generation into other JVM services.

- `snowflake-proto`
  - Source of truth for API contracts (`.proto` files).
  - Generates gRPC stubs and protobuf classes used by both client and server.

- `snowflake-loadtest`
  - Gatling-based load testing module for gRPC ID generation throughput checks.
  - Contains parameterized simulations for local or environment testing.

- `helm/snowflake-id-service`
  - Helm chart for Kubernetes deployment.
  - Includes configurable values for image, replicas, probes, resources, and ingress.

- `Dockerfile`
  - Container image definition for packaging the server module.

## Module Versioning

Module versions are managed independently in `gradle.properties`:

- `snowflakeProtoVersion`
- `snowflakeClientVersion`
- `snowflakeServerVersion`
- `snowflakeLoadtestVersion`

Update these values when releasing each module.

## Tech Stack

- Java 21
- Spring Boot
- gRPC + Protocol Buffers
- Gradle (multi-module)
- Docker
- Helm

## Build

Build all modules:

```bash
./gradlew clean build
```

## Run Locally

Start the gRPC server:

```bash
./gradlew :snowflake-server:bootRun
```

Default gRPC port: `9090`

## Client Usage

### With `SnowflakeClient`

```java
try (SnowflakeClient client = new SnowflakeClient("localhost", 9090)) {
    long id = client.generateId();
    System.out.println("Generated ID: " + id);
}
```

### With Spring Boot Auto-Configuration

`application.yaml`:

```yaml
snowflake:
  client:
    host: localhost
    port: 9090
```

Then inject `SnowflakeClient` in your service.

## Load Testing (Gatling)

Run the default simulation:

```bash
./gradlew :snowflake-loadtest:gatlingRun --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation
```

Override runtime parameters:

```bash
./gradlew :snowflake-loadtest:gatlingRun \
  --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation \
  -Dsnowflake.host=localhost \
  -Dsnowflake.port=9090 \
  -Dsnowflake.users=200 \
  -Dsnowflake.rampSeconds=30 \
  -Dsnowflake.requestsPerUser=1000 \
  -Dsnowflake.pauseMs=0 \
  -Dsnowflake.callDeadlineMs=1000
```

## GitLab CI

The repository includes a starter pipeline in `.gitlab-ci.yml` with these stages:

- `build` (`./gradlew clean assemble`)
- `test` (`./gradlew test`)
- `loadtest` (starts `snowflake-server` and runs Gatling simulation)

Run jobs locally with GitLab Runner:

```bash
gitlab-runner exec docker build
gitlab-runner exec docker test
gitlab-runner exec docker loadtest
```

Override load test parameters for local runner:

```bash
gitlab-runner exec docker \
  --env SNOWFLAKE_USERS=200 \
  --env SNOWFLAKE_RAMP_SECONDS=30 \
  --env SNOWFLAKE_REQUESTS_PER_USER=1000 \
  loadtest
```

Run GitLab Runner inside Docker (no host install):

```bash
docker compose -f docker-compose.gitlab-runner.yml up -d
docker compose -f docker-compose.gitlab-runner.yml exec gitlab-runner gitlab-runner exec docker build
docker compose -f docker-compose.gitlab-runner.yml exec gitlab-runner gitlab-runner exec docker test
docker compose -f docker-compose.gitlab-runner.yml exec gitlab-runner gitlab-runner exec docker loadtest
```

Stop the runner container:

```bash
docker compose -f docker-compose.gitlab-runner.yml down
```

One-command local pipeline:

```bash
./scripts/run-local-ci.sh
```

This script runs `build`, `test`, and `loadtest`, then opens JaCoCo and Gatling reports in your browser.
It tries `gitlab-runner exec docker` first and falls back to direct Gradle-in-Docker execution if local runner exec is unavailable.

## API Contract

Proto definition:

- `snowflake-proto/src/main/proto/snowflake.proto`

The main RPC returns a generated 64-bit ID.

## Container

Build image:

```bash
docker build -t snowflake-id-service:latest .
```

Run image:

```bash
docker run --rm -p 9090:9090 snowflake-id-service:latest
```

## Kubernetes (Helm)

Install the chart:

```bash
helm install snowflake-id-service ./helm/snowflake-id-service
```

Customize deployment via `helm/snowflake-id-service/values.yaml`.

## Notes

- This repository intentionally avoids embedding personal/private registry coordinates in examples.
- If you publish artifacts or images, replace placeholders with your own organization/repository names.
