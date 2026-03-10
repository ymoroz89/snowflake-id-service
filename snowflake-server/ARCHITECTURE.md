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

#### Kubernetes Integration
- **Hostname-based Node ID**: Extracts ordinal from pod hostname (e.g., `snowflake-1` → node ID 1)
- **State File Path**: Configurable via environment variables
- **Container-friendly**: Designed for stateful set deployment

### Configuration

#### Environment Variables
- `HOSTNAME`: Kubernetes pod hostname (auto-extracted for node ID)
- `snowflake.state.file`: State file path (default: `/data/snowflake.state`)

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
- **StatefulSet**: Recommended for stable hostname-based node IDs
- **Persistent Volume**: Required for state file persistence
- **Resource Limits**: CPU and memory constraints based on expected load

#### Monitoring
- **Metrics**: ID generation rate, latency, error rates
- **Health Checks**: Service availability and state persistence
- **Logging**: Comprehensive operation logging for debugging

#### Scaling
- **Horizontal Scaling**: Add more pods for increased capacity
- **Node ID Management**: Ensure unique hostnames in StatefulSet
- **State Isolation**: Each pod maintains independent state

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
