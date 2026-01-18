# snowflake-id-service

A gRPC service for generating unique Snowflake IDs.

## Architecture

The project is structured as a multi-module Gradle build:

- `snowflake-proto`: Protocol buffer definitions and generated gRPC client/server stubs
- `snowflake-server`: gRPC server implementation using Spring Boot
- `snowflake-client`: Java gRPC client library for easy integration

## Building

Build all modules:
```shell
./gradlew build
```

## Running the Server

Run the gRPC server:
```shell
./gradlew :snowflake-server:bootRun
```

The server will start on port 9090.

## Using the Client

### Option 1: Using SnowflakeClient (Recommended)

Add the `snowflake-client` dependency:

```gradle
dependencies {
    implementation("com.ymoroz.snowflake:snowflake-client:0.0.1-SNAPSHOT")
}
```

Example usage:

```java
try (SnowflakeClient client = new SnowflakeClient("localhost", 9090)) {
    long id = client.generateId();
    System.out.println("Generated ID: " + id);
}
```

### Option 2: Using snowflake-proto directly

The `snowflake-proto` module can be used as a Gradle dependency in other projects:

```gradle
dependencies {
    implementation("com.ymoroz.snowflake:snowflake-proto:0.0.1-SNAPSHOT")
}
```

Example client usage:

```java
ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
    .usePlaintext()
    .build();

SnowflakeServiceGrpc.SnowflakeServiceBlockingStub stub =
    SnowflakeServiceGrpc.newBlockingStub(channel);

GenerateIdResponse response = stub.generateId(GenerateIdRequest.newBuilder().build());
long id = response.getId();

channel.shutdown();
```

## Docker

Build the Docker image:
```shell
docker build -t snowflake-id-service .
```

Tag and push:
```shell
docker image tag snowflake-id-service ymoroz/snowflake-id-service:latest
docker push ymoroz/snowflake-id-service:latest
```

## Kubernetes Deployment

Load image into Kind:
```shell
kind load docker-image ymoroz/snowflake-id-service:latest
```

Package and install Helm chart:
```shell
helm package helm/snowflake-id-service
helm install snowflake-id-service ./snowflake-id-service-0.1.0.tgz
```
