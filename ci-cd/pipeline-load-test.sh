#!/usr/bin/env bash
set -euo pipefail

# Determine the project root based on the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")

wait_for_service() {
  local host="$1"
  local port="$2"
  local service_name="$3"

  echo "Waiting for $service_name to be ready at ${host}:${port}"
  for i in $(seq 1 90); do
    # Use nc (netcat) for a more reliable check with a timeout
    if nc -z -w 5 "$host" "$port" >/dev/null 2>&1; then
      echo "$service_name is ready."
      return 0
    fi
    echo "Still waiting for $service_name... ($i/90)"
    sleep 2
  done

  echo "Error: Timed out waiting for $service_name startup at ${host}:${port}"
  exit 1
}

loadtest() {
  echo "========================================"
  echo "Stage: loadtest"
  echo "========================================"

  # The snowflake-server is running in Kubernetes, exposed via Ingress.
  local snowflake_host="localhost"
  local snowflake_port="443"
  
  wait_for_service "$snowflake_host" "$snowflake_port" "Snowflake gRPC Service"
  
  echo "Running load test against Kubernetes cluster"
  ./gradlew :snowflake-loadtest:gatlingRun \
    --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation \
    -Dsnowflake.host=${snowflake_host} \
    -Dsnowflake.port=${snowflake_port} \
    -Dsnowflake.users=1000 \
    -Dsnowflake.rampSeconds=100 \
    -Dsnowflake.requestsPerUser=1000 \
    -Dsnowflake.pauseMs=0 \
    -Dsnowflake.callDeadlineMs=1000 \
    -PgatlingJvmArgs="-Xms1g -Xmx4g"

  echo "Load test finished."
  echo
}

main() {
    echo "========================================"
    echo "Starting load test pipeline"
    echo "========================================"
    echo
  loadtest
    echo "========================================"
    echo "All stages completed successfully!"
    echo "========================================"
}

main "$@"
