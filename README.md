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

- Java 21, Scala (Gatling load tests)
- Spring Boot 4.0.2 + Spring gRPC
- gRPC + Protocol Buffers
- Gradle (multi-module)
- Lombok
- PMD (static analysis), JaCoCo (code coverage)
- Docker (distroless non-root image)
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

## CI/CD Pipeline

The project includes a comprehensive local CI/CD pipeline in the `ci-cd/` directory with the following stages:

### Pipeline Stages

1. **build**: Compiles the project using Gradle
2. **test**: Runs all unit tests
3. **docker-build**: Builds Docker image with Git commit hash and `latest` tags
4. **docker-publish**: Publishes image to Docker Hub (requires authentication)
5. **deploy**: Deploys to local Kubernetes cluster using Kind and Helm
6. **loadtest**: Runs Gatling load tests against local server
7. **cleanup**: Cleans up Kubernetes cluster and Docker images (with confirmation)

### Running the Pipeline

#### One-Command Execution

Run the complete pipeline:

```bash
./ci-cd/local-pipeline.sh
```

#### Stage-by-Stage Execution

Run individual stages:

```bash
# Build stage
./ci-cd/stage-build.sh

# Test stage
./ci-cd/stage-test.sh

# Docker build stage
./ci-cd/stage-docker-build.sh

# Docker publish stage
./ci-cd/stage-docker-publish.sh

# Deploy to Kubernetes
./ci-cd/stage-deploy.sh

# Load test stage
./ci-cd/stage-loadtest.sh

# Cleanup stage
./ci-cd/stage-cleanup.sh
```

### Configuration

The pipeline uses `ci-cd/local.env` for Docker Hub credentials and optional Grafana password:

```bash
export DOCKERHUB_USERNAME=your-username
export DOCKERHUB_PASSWORD=your-password
export GRAFANA_ADMIN_PASSWORD=change-me
```

### Key Features

- Automatic Docker daemon detection and startup
- Kind Kubernetes cluster management
- Helm chart deployment with configurable values
- Gatling load testing with predefined parameters
- Cleanup with interactive confirmation for cluster and images
- Comprehensive logging to `ci-cd/ci-cd.log`
- Parallel execution of stages with clear separation

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

## Observability

The service exposes Prometheus metrics on `http://<pod-ip>:8080/actuator/prometheus`.

When running `./ci-cd/stage-deploy.sh`, the pipeline installs `kube-prometheus-stack` in the `monitoring` namespace and enables app scraping via `ServiceMonitor`.

- Grafana URL: `http://localhost:30300` (user `admin`, password from `GRAFANA_ADMIN_PASSWORD` or `admin`)
- Prometheus URL: `http://localhost:30091`
- Stack values file: `k8s/observability/kube-prometheus-stack-values.yaml`

## Notes

- This repository intentionally avoids embedding personal/private registry coordinates in examples.
- If you publish artifacts or images, replace placeholders with your own organization/repository names.
