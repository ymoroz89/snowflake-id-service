# Snowflake Server Architecture

Main repository documentation: [../README.md](../README.md)

Module overview: [README.md](README.md)

## System Design Overview

This package implements a distributed ID generation service based on Twitter's Snowflake algorithm, designed for high-performance, globally unique ID generation in distributed systems.

## Architecture

### Core Components

#### `SnowflakeService`
The main service class that implements the Snowflake algorithm for generating unique 64-bit IDs.

**Key Design Principles:**
- **Thread Safety**: Uses `ReentrantLock` to ensure atomic ID generation across concurrent requests
- **State Persistence**: Integrates with `SnowflakeStateService` to prevent duplicate IDs across service restarts
- **Clock Drift Protection**: Implements automatic recovery mechanisms for system clock adjustments
- **Kubernetes Integration**: Extracts node ID from pod hostname for seamless container deployment

**ID Structure:**
```
63...42 41...12 11...0
|       |       |
|       |       └── Sequence (12 bits) - 4096 IDs per ms per node
|       └── Node ID (10 bits) - Supports up to 1024 nodes
└── Timestamp (41 bits) - Milliseconds since custom epoch
```

#### `SnowflakeStateService`
Manages state persistence to ensure ID uniqueness across service restarts.

**Key Features:**
- **Time Window Reservation**: Saves current timestamp + buffer to reserve future time windows
- **Clock Drift Recovery**: Automatically waits for system time to catch up if behind saved state
- **Thread-Safe Operations**: Uses file channel operations with proper synchronization
- **Error Handling**: Comprehensive logging and exception handling for state management failures

### Design Patterns

#### Singleton Pattern
- Service is managed by Spring's dependency injection container
- Ensures single instance per application context

#### Strategy Pattern
- State management is abstracted through `SnowflakeStateService`
- Allows for different persistence strategies (file-based, database, etc.)

#### Observer Pattern
- Scheduled executor observes time progression and triggers state saves
- Automatic state persistence every second

#### Factory Pattern
- Node ID extraction from hostname provides flexible deployment strategies

### Concurrency Model

#### Thread Safety
- **ReentrantLock**: Ensures atomic ID generation operations
- **Virtual Threads**: Uses virtual thread executor for high concurrency testing
- **Atomic Operations**: Sequence increment and timestamp comparison are atomic

#### Performance Characteristics
- **Throughput**: ~4 million IDs/second per node
- **Latency**: Sub-microsecond ID generation
- **Scalability**: Linear scaling with node count (up to 1024 nodes)

### State Management

#### Persistence Strategy
- **File-based Storage**: State saved to configurable file path (`/data/snowflake.state`)
- **Buffered Writes**: Uses 3-second buffer to reserve time windows
- **Periodic Saves**: Automatic state persistence every second via scheduled executor

#### Recovery Mechanisms
- **Clock Drift Protection**: Automatic waiting when system time is behind saved state
- **Graceful Shutdown**: Final state save during service destruction
- **Error Recovery**: Comprehensive exception handling with service continuation

### Integration Points

#### gRPC Interface
- `SnowflakeGrpcService` provides gRPC interface for ID generation
- Protocol buffer definitions in `snowflake-proto` package
- Supports distributed access across services

#### Unary RPC Stream Lifecycle
The `generateId` method implements a Unary RPC pattern with the following lifecycle:

**Stream Flow**:
1. **Client Request**: Client sends `GenerateIdRequest` (empty message)
2. **Server Processing**: 
   - Calls `snowflakeService.nextId()` to generate unique ID
   - Records timing metrics using Micrometer
   - Increments generation counter
3. **Response Building**: Creates `GenerateIdResponse` with the generated ID
4. **Response Transmission**: Sends response via `responseObserver.onNext(response)`
5. **Stream Closure**: Signals completion with `responseObserver.onCompleted()`

**Stream Behavior**:
- **Single Use**: Each Unary RPC call creates a new stream used exactly once
- **Automatic Cleanup**: Stream is automatically closed after `onCompleted()` is called
- **Resource Efficient**: No persistent connection maintained after response
- **HTTP/2 Multiplexing**: Multiple Unary RPC calls can share the same underlying TCP connection

**Client-Side Handling**:
```java
stub.generateId(request, new StreamObserver<GenerateIdResponse>() {
    @Override
    public void onNext(GenerateIdResponse response) {
        // Called when response arrives
        System.out.println("Received ID: " + response.getId());
    }
    
    @Override
    public void onError(Throwable t) {
        // Called if an error occurs (stream closes automatically)
        System.err.println("Error: " + t.getMessage());
    }
    
    @Override
    public void onCompleted() {
        // Called when stream is closed by server
        System.out.println("Stream completed - connection closed");
    }
});
```

**Key Characteristics**:
- **Request-Response Pattern**: One request → One response → Stream closed
- **Stateless**: Each call is independent with no persistent connection
- **Type Safety**: Protocol Buffers provide compile-time type checking
- **Performance**: Binary protocol with HTTP/2 foundation for optimal performance

#### Kubernetes Integration
- **Hostname-based Node ID**: Extracts ordinal from pod hostname (e.g., `snowflake-1` → node ID 1)
- **State File Path**: Controlled by `state.file` property (default `/data/snowflake.state` in this project).
- **Container-friendly**: Designed for stateful set deployment

#### Helm Deployment Topology (`helm/snowflake-id-service`)
- **Workload**: `StatefulSet` with persistent storage (`/data`) and stable pod identities.
- **Networking**:
  - App service: NodePort service on `9090` (default node port `30090`) for gRPC.
  - Headless service: used for StatefulSet DNS identity.
  - Ingress: enabled by default with `nginx` class and `nginx.ingress.kubernetes.io/backend-protocol: GRPC`.
  - TLS: enabled by default, secret `snowflake-id-service-tls`, default host `localhost`.
- **Ports exposed by container**:
  - gRPC API: `9090`
  - gRPC health endpoint: `9091`
  - HTTP metrics endpoint: `8080` (`/actuator/prometheus`)
- **Autoscaling**:
  - HPA is enabled by default and targets the StatefulSet.
  - Defaults: `minReplicas=3`, `maxReplicas=15`, CPU target `70%`, memory target `80%`.
- **Observability resources**:
  - Dedicated metrics service (`*-metrics`) is enabled by default.
  - `ServiceMonitor` is enabled by default for Prometheus Operator setups.
  - Companion Helm values in this repo expose Grafana/Prometheus via NodePorts (`30300`/`30091`).
- **Ingress controller defaults in this repo**:
  - `helm/ingress-nginx/values.yaml` configures ingress-nginx HTTPS NodePort `30443`.

#### Local Infrastructure Orchestration (`infra/`)
- `infra/pipeline-environment-set-up.sh` prepares local infrastructure before app deployment.
- `infra/kind-cluster.sh create` ensures Kind cluster `dev-cluster` and installs Metrics Server for HPA metrics.
  - Applies upstream Metrics Server manifest.
  - Adds `--kubelet-insecure-tls`.
  - Scales Metrics Server to 3 replicas and validates `kubectl top nodes`.
- `infra/create-tls-secret.sh` generates/reuses self-signed certs for `localhost` in `certs/` and creates Kubernetes secret `snowflake-id-service-tls`.
- Installs `ingress-nginx` and `kube-prometheus-stack` via repository-local values files:
  - `helm/ingress-nginx/values.yaml`
  - `helm/kube-prometheus-stack/values.yaml`
- Kind control-plane host port mappings (`infra/kind/kind-config.yaml`):
  - `443 -> 30443` (ingress-nginx HTTPS entrypoint)
  - `30090 -> 30090` (service NodePort exposure)
  - `30091 -> 30091` (Prometheus)
  - `30300 -> 30300` (Grafana)

### Configuration

#### Environment Variables
- `HOSTNAME`: Kubernetes pod name (used to derive node ID from the numeric suffix).
- `GRPC_SERVER_PORT`: Helm chart sets this to app gRPC port (`9090` by default).
- `APP_HEALTH_PORT`: Health gRPC server port (default Helm value `9091`).
- `SERVER_PORT`: HTTP management/metrics port (default Helm value `8080`).
- `STATE_FILE`: Set by Helm chart to `/data/snowflake.state` (binds to `state.file`).
- `GRAFANA_ADMIN_PASSWORD`: Used by infra setup when installing `kube-prometheus-stack` (defaults to `admin`).

#### Application Properties
- `state.file`: State file location used by `SnowflakeStateService` (default `/data/snowflake.state`).
- `snowflake.hostname`: Hostname used for node ID parsing (defaults to `${HOSTNAME:snowflake-0}`).
- `snowflake.custom-epoch`, `snowflake.node-id-bits`, `snowflake.sequence-bits`, `snowflake.time-offset-buffer-ms`.

#### Customization Points
- **Custom Epoch**: Configurable base timestamp (default: 2015-01-01)
- **Buffer Size**: Time window reservation buffer (configurable)
- **State Save Interval**: Frequency of state persistence (configurable)

### Error Handling

#### Clock Drift Protection
- Detects system clock adjustments
- Automatically waits for time to catch up
- Prevents duplicate ID generation during clock corrections

#### State Management Errors
- Comprehensive logging for state file operations
- Graceful degradation when state persistence fails
- Service continues operation even if state save fails

#### Concurrency Safety
- Lock timeout protection
- Deadlock prevention through simple lock ordering
- Thread interruption handling

### Testing Strategy

#### Unit Tests (`SnowflakeServiceTest`)
- ID generation correctness
- Clock drift behavior
- Node ID extraction from hostnames
- Edge case handling

#### Concurrency Tests (`SnowflakeServiceConcurrencyTest`)
- Thread safety validation
- Uniqueness verification under high load
- Performance characteristics validation

#### Integration Tests
- End-to-end gRPC functionality
- State persistence across restarts
- Clock drift recovery scenarios

### Deployment Considerations

#### Kubernetes Deployment
- **StatefulSet**: Used by Helm chart for stable hostname-based node IDs.
- **Persistent Volume**: Enabled by default (`ReadWriteOnce`, `10Mi`) for `/data/snowflake.state`.
- **Security Context**: Runs as non-root (`runAsUser=65532`), drops capabilities, read-only root filesystem.
- **Graceful Termination**: Pre-stop hook sleeps 30s; termination grace period is 45s.
- **Local Cluster Baseline**: Kind cluster has one control-plane + two workers in this repository configuration.

#### Monitoring
- **Metrics**: ID generation counters/latency plus Spring Actuator Prometheus endpoint on `:8080`.
- **Health Checks**: Kubernetes gRPC liveness/readiness probes on port `9091`.
- **ServiceMonitor**: Chart can expose metrics to Prometheus Operator via `ServiceMonitor`.
- **Logging**: Comprehensive operation logging for debugging.

#### Scaling
- **Horizontal Scaling**: Managed by HPA (`autoscaling/v2`) against CPU and memory utilization.
- **Node ID Management**: Hostname suffix is used as node ID, so stable pod naming is required.
- **State Isolation**: Each pod has its own PVC and state file.

### Performance Characteristics

#### Theoretical Limits
- **Node Capacity**: 1024 nodes maximum
- **Rate per Node**: 4,096 IDs per millisecond
- **Total Capacity**: ~4.2 million IDs/second per node
- **Time Range**: ~69 years from custom epoch

#### Real-world Performance
- **Latency**: Sub-microsecond generation time
- **Throughput**: Limited by system I/O and CPU
- **Memory Usage**: Minimal (single service instance)
- **Storage**: Minimal state file (8 bytes)

### Security Considerations

#### ID Predictability
- IDs are time-ordered and partially predictable
- Suitable for internal use, not cryptographic applications
- Node ID and sequence provide some obfuscation

#### State File Security
- File permissions should restrict access
- State file contains sensitive timing information
- Consider encryption for sensitive environments

This design provides a robust, high-performance solution for distributed ID generation while maintaining simplicity and reliability in production environments.
