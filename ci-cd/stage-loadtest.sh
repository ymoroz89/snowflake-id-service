#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[loadtest] %s\n' "$*"
}

loadtest() {
  log "========================================"
  log "Stage: loadtest"
  log "========================================"
  
  log "Starting snowflake-server"
  ./gradlew :snowflake-server:bootRun > server.log 2>&1 &
  echo $! > server.pid
  
  log "Waiting for snowflake-server to be ready"
  local snowflake_host="localhost"
  local snowflake_port="443"
  
  for i in $(seq 1 90); do
    if (echo > /dev/tcp/${snowflake_host}/${snowflake_port}) >/dev/null 2>&1; then
      log "snowflake-server is ready on ${snowflake_host}:${snowflake_port}"
      break
    fi
    sleep 1
    if [ "$i" -eq 90 ]; then
      log "Timed out waiting for snowflake-server startup"
      cat server.log
      exit 1
    fi
  done
  
  log "Running load test"
  ./gradlew :snowflake-loadtest:gatlingRun \
    --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation \
    -Dsnowflake.host=${snowflake_host} \
    -Dsnowflake.port=${snowflake_port} \
    -Dsnowflake.users=1000 \
    -Dsnowflake.rampSeconds=120 \
    -Dsnowflake.requestsPerUser=1000 \
    -Dsnowflake.pauseMs=0 \
    -Dsnowflake.callDeadlineMs=1000
  
  log "Stopping snowflake-server"
  if [ -f server.pid ]; then
    kill "$(cat server.pid)" || true
  fi
  
  echo
}

main() {
  loadtest
}

main "$@"