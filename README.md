# snowflake-id-service

Distributed 64-bit ID generation service based on the Snowflake algorithm, exposed via gRPC.

## Why This Project

This repository provides a production-oriented Snowflake setup with:

- A gRPC server for high-throughput unique ID generation.
- Shared protobuf contracts for cross-module compatibility.
- A reusable Java client with Spring Boot auto-configuration.
- Docker and Helm assets for local Kubernetes deployment.
- Gatling load tests for throughput and latency validation.

## Repository Structure

| Path | Purpose |
| --- | --- |
| `snowflake-server` | Spring Boot + gRPC server with Snowflake ID generation logic. [Module README](snowflake-server/README.md), [Architecture](snowflake-server/ARCHITECTURE.md) |
| `snowflake-client` | Java gRPC client library (`SnowflakeClient`) + Spring Boot auto-config |
| `snowflake-proto` | Protobuf/gRPC API contract and generated stubs |
| `snowflake-loadtest` | Gatling load-testing module for gRPC traffic |
| `helm/snowflake-id-service` | Helm chart for app deployment. [Chart README](helm/snowflake-id-service/README.md) |
| `infra` | Kind cluster and local infrastructure setup scripts |
| `ci-cd` | Build/test/image/deploy/load-test pipeline scripts |

## Module Documentation

- Server module README: [snowflake-server/README.md](snowflake-server/README.md)
- Server architecture: [snowflake-server/ARCHITECTURE.md](snowflake-server/ARCHITECTURE.md)

## Prerequisites

For local development:

- Java 21
- Docker

For local Kubernetes pipeline:

- Kind
- kubectl
- Helm 3

## Quick Start (Local gRPC Server)

1. Build all modules:

```bash
./gradlew clean build
```

2. Start the server locally:

```bash
SNOWFLAKE_HOSTNAME=snowflake-0 \
STATE_FILE=/tmp/snowflake.state \
./gradlew :snowflake-server:bootRun
```

3. Use the Java client:

```java
try (SnowflakeClient client = new SnowflakeClient("localhost", 9090)) {
    long id = client.generateId();
    System.out.println("Generated ID: " + id);
}
```

Spring Boot auto-configuration (`snowflake-client`):

```yaml
snowflake:
  client:
    host: localhost
    port: 9090
    keepAliveTime: 30
    keepAliveTimeout: 5
```

Notes:

- `SNOWFLAKE_HOSTNAME` should end with `-<number>` (for example `snowflake-0`) because node ID is parsed from the hostname suffix.
- `STATE_FILE` is set to a writable local path for development.

## API Contract

Protocol definition: `snowflake-proto/src/main/proto/snowflake.proto`

Service:

- `SnowflakeService.GenerateId(GenerateIdRequest) -> GenerateIdResponse`

## Configuration

Server (`snowflake-server/src/main/resources/application.yaml`):

- `snowflake.custom-epoch`
- `snowflake.node-id-bits`
- `snowflake.sequence-bits`
- `snowflake.time-offset-buffer-ms`
- `snowflake.hostname`
- `state.file`
- `app.health.port`

Client (`snowflake-client`):

- `snowflake.client.host` (default `localhost`)
- `snowflake.client.port` (default `9090`)
- `snowflake.client.keepAliveTime` (default `30` seconds)
- `snowflake.client.keepAliveTimeout` (default `5` seconds)

## Build and Test

Run full build:

```bash
./gradlew clean build
```

Run tests only:

```bash
./gradlew test
```

## Load Testing (Gatling)

Default run:

```bash
./gradlew :snowflake-loadtest:gatlingRun --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation
```

Example overrides:

```bash
./gradlew :snowflake-loadtest:gatlingRun \
  --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation \
  -Dsnowflake.host=localhost \
  -Dsnowflake.port=443 \
  -Dsnowflake.useTls=true \
  -Dsnowflake.users=1000 \
  -Dsnowflake.rampSeconds=100 \
  -Dsnowflake.requestsPerUser=1000 \
  -Dsnowflake.keepAliveTime=30 \
  -Dsnowflake.keepAliveTimeout=5
```

Load-test networking options:

- `snowflake.keepAliveTime` (default `30` seconds)
- `snowflake.keepAliveTimeout` (default `5` seconds)

## Local Pipeline (Kind + Helm)

Run the end-to-end local flow:

```bash
./local-run.sh
```

This orchestrates:

1. `infra/pipeline-environment-set-up.sh`
2. `ci-cd/pipeline-build.sh`
3. `ci-cd/pipeline-deployment.sh`
4. Optional `ci-cd/pipeline-load-test.sh`

Infra behavior used by this flow:

- Kind cluster name: `dev-cluster`
- Host port mappings: `443 -> ingress`, `30090 -> service NodePort`, `30091 -> Prometheus`, `30300 -> Grafana`
- TLS secret `snowflake-id-service-tls` is created from local `certs/` (self-signed for `localhost` if absent)
- Metrics Server is installed and validated (`kubectl top`) for HPA support

Optional credentials for Docker Hub publish/pull and Grafana admin password can be provided in root `local.env`:

```bash
export DOCKERHUB_USERNAME=your-username
export DOCKERHUB_PASSWORD=your-password-or-token
export GRAFANA_ADMIN_PASSWORD=change-me
```

You can also run stages directly:

```bash
./ci-cd/stage-build.sh
./ci-cd/stage-test.sh
./ci-cd/stage-docker-build.sh
./ci-cd/stage-docker-publish.sh
```

Cleanup:

```bash
./cleanup.sh
```

## Docker

Build image:

```bash
docker build -t snowflake-id-service:latest .
```

Run image:

```bash
docker run --rm -p 9090:9090 -p 9091:9091 snowflake-id-service:latest
```

## Kubernetes (Helm)

Install chart:

```bash
helm upgrade --install snowflake-id-service ./helm/snowflake-id-service
```

Customize via `helm/snowflake-id-service/values.yaml`.

## Observability

When the local infra pipeline is deployed:

- Grafana: `http://localhost:30300`
- Prometheus: `http://localhost:30091`

Application metrics endpoint:

- `http://<pod-ip>:8080/actuator/prometheus`

## Project Status

Actively maintained and used for local development, Kubernetes deployment, and load-testing workflows.

## Support

Open an issue for bugs, questions, or feature requests:

- <https://github.com/ymoroz89/snowflake-id-service/issues>

## Contributing

Contributions are welcome.

1. Fork and create a feature branch.
2. Make your changes with tests.
3. Run `./gradlew clean test`.
4. Open a pull request.

## Maintainer

- [@ymoroz89](https://github.com/ymoroz89)

## License

No `LICENSE` file is currently present in this repository.
