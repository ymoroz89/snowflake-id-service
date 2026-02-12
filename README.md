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

- `helm/snowflake-id-service`
  - Helm chart for Kubernetes deployment.
  - Includes configurable values for image, replicas, probes, resources, and ingress.

- `Dockerfile`
  - Container image definition for packaging the server module.

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
