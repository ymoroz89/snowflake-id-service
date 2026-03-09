#!/usr/bin/env bash
set -euo pipefail

# Determine the project root based on the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")

# Set up logging
LOG_FILE="$PROJECT_ROOT/ci-cd/load-test.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[loadtest] %s\n' "$*"
}

wait_for_service() {
  local host="$1"
  local port="$2"
  local service_name="$3"

  log "Waiting for $service_name to be ready at ${host}:${port}"
  for i in $(seq 1 90); do
    # Use nc (netcat) for a more reliable check with a timeout
    if nc -z -w 5 "$host" "$port" >/dev/null 2>&1; then
      log "$service_name is ready."
      return 0
    fi
    log "Still waiting for $service_name... ($i/90)"
    sleep 2
  done

  log "Error: Timed out waiting for $service_name startup at ${host}:${port}"
  exit 1
}

loadtest() {
  log "========================================"
  log "Stage: loadtest"
  log "========================================"

  # The snowflake-server is running in Kubernetes, exposed via Ingress.
  local snowflake_host="localhost"
  local snowflake_port="443"
  
  wait_for_service "$snowflake_host" "$snowflake_port" "Snowflake gRPC Service"
  
  log "Running load test against Kubernetes cluster"
  ./gradlew :snowflake-loadtest:gatlingRun \
    --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation \
    -Dsnowflake.host=${snowflake_host} \
    -Dsnowflake.port=${snowflake_port} \
    -Dsnowflake.users=1000 \
    -Dsnowflake.rampSeconds=300 \
    -Dsnowflake.requestsPerUser=500 \
    -Dsnowflake.pauseMs=0 \
    -Dsnowflake.callDeadlineMs=1000 \
    -PgatlingJvmArgs="-Xmx2G -Djavax.net.ssl.trustStore=$PROJECT_ROOT/certs/tls.crt"

  log "Load test finished."
  echo
}

main() {
    log "========================================"
    log "Starting load test pipeline"
    log "========================================"
    echo
  loadtest
    log "========================================"
    log "All stages completed successfully!"
    log "========================================"
}

main "$@"
