#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="docker-compose.gitlab-runner.yml"
SERVICE="gitlab-runner"
GRADLE_IMAGE="gradle:8.11.1-jdk21"

log() {
  printf '[local-ci] %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

start_docker_if_needed() {
  if docker info >/dev/null 2>&1; then
    log "Docker daemon is already running"
    return
  fi

  log "Docker daemon is not running; attempting to start it"
  local os
  os="$(uname -s)"

  case "$os" in
    Darwin)
      if command -v open >/dev/null 2>&1; then
        open -a Docker >/dev/null 2>&1 || true
      fi
      ;;
    Linux)
      if command -v systemctl >/dev/null 2>&1; then
        systemctl --user start docker >/dev/null 2>&1 || true
        sudo systemctl start docker >/dev/null 2>&1 || true
      fi
      ;;
  esac

  log "Waiting for Docker daemon"
  for _ in $(seq 1 60); do
    if docker info >/dev/null 2>&1; then
      log "Docker daemon is ready"
      return
    fi
    sleep 2
  done

  log "Failed to connect to Docker daemon. Start Docker manually and retry."
  exit 1
}

run_job() {
  local job="$1"
  log "Running GitLab job: ${job}"
  docker compose -f "$COMPOSE_FILE" exec -T "$SERVICE" gitlab-runner exec docker "$job"
}

run_gradle_container() {
  local cmd="$1"
  docker run --rm \
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp \
    -e GRADLE_USER_HOME=/tmp/.gradle \
    -v "$PWD:/workspace" \
    -w /workspace \
    "$GRADLE_IMAGE" \
    bash -lc "$cmd"
}

run_jobs_fallback() {
  log "Falling back to direct Gradle-in-Docker execution"

  run_gradle_container "./gradlew clean assemble"
  run_gradle_container "./gradlew test"

  run_gradle_container '
    set -euo pipefail
    ./gradlew -q help >/dev/null
    ./gradlew :snowflake-server:bootRun > server.log 2>&1 & echo $! > server.pid
    for i in $(seq 1 300); do
      if ! kill -0 "$(cat server.pid)" >/dev/null 2>&1; then
        echo "snowflake-server process exited before becoming ready"
        cat server.log
        exit 1
      fi
      if (echo > /dev/tcp/localhost/9090) >/dev/null 2>&1; then
        break
      fi
      sleep 1
      if [ "$i" -eq 300 ]; then
        echo "Timed out waiting for snowflake-server startup"
        cat server.log
        exit 1
      fi
    done
    ./gradlew :snowflake-loadtest:gatlingRun \
      --simulation com.ymoroz.snowflake.loadtest.SnowflakeGrpcSimulation \
      -Dsnowflake.host=localhost \
      -Dsnowflake.port=9090 \
      -Dsnowflake.users=100 \
      -Dsnowflake.rampSeconds=20 \
      -Dsnowflake.requestsPerUser=100 \
      -Dsnowflake.pauseMs=0 \
      -Dsnowflake.callDeadlineMs=1000
    if [ -f server.pid ]; then
      kill "$(cat server.pid)" || true
    fi
  '
}

open_report() {
  local report_file="$1"

  if [ ! -f "$report_file" ]; then
    return
  fi

  if command -v open >/dev/null 2>&1; then
    open "$report_file" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$report_file" >/dev/null 2>&1 || true
  elif command -v start >/dev/null 2>&1; then
    start "" "$report_file" >/dev/null 2>&1 || true
  fi

  log "Report: $report_file"
}

open_reports() {
  local jacoco_reports=()
  local gatling_report=""

  while IFS= read -r report; do
    jacoco_reports+=("$report")
  done < <(find . -type f -path "*/build/reports/jacoco/test/html/index.html" | sort)

  if [ -d "snowflake-loadtest/build/reports/gatling" ]; then
    gatling_report="$(find snowflake-loadtest/build/reports/gatling -type f -name index.html | sort | tail -n1)"
  fi

  if [ "${#jacoco_reports[@]}" -eq 0 ] && [ -z "$gatling_report" ]; then
    log "No JaCoCo or Gatling reports found"
    return
  fi

  log "Opening generated reports in browser"

  for report in "${jacoco_reports[@]}"; do
    open_report "$report"
  done

  if [ -n "$gatling_report" ]; then
    open_report "$gatling_report"
  fi
}

main() {
  ensure_cmd docker

  log "Starting local GitLab pipeline (build -> test -> loadtest)"
  start_docker_if_needed

  log "Starting GitLab Runner container"
  docker compose -f "$COMPOSE_FILE" up -d "$SERVICE"

  if run_job build && run_job test && run_job loadtest; then
    log "Local pipeline finished successfully (gitlab-runner mode)"
  else
    log "gitlab-runner local execution failed"
    run_jobs_fallback
    log "Local pipeline finished successfully (fallback mode)"
  fi

  open_reports
}

main "$@"
