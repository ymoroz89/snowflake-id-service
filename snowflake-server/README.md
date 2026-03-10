# snowflake-server

gRPC server module for distributed 64-bit Snowflake ID generation.

## Documents

- Module architecture and design details: [ARCHITECTURE.md](ARCHITECTURE.md)
- Main project README: [../README.md](../README.md)

## Run Locally

```bash
SNOWFLAKE_HOSTNAME=snowflake-0 \
STATE_FILE=/tmp/snowflake.state \
./gradlew :snowflake-server:bootRun
```

## Test

```bash
./gradlew :snowflake-server:test
```
