#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[docker-build] %s\n' "$*"
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

docker_build() {
  log "========================================"
  log "Stage: docker-build"
  log "========================================"
  
  ensure_cmd docker
  
  log "Starting Docker daemon"
  start_docker_if_needed
  
  log "Building Docker image"
  docker build -t "snowflake-id-service:$(git rev-parse --short HEAD)" -t "snowflake-id-service:latest" .
  
  log "Saving Docker image"
  docker save "snowflake-id-service:latest" -o snowflake-id-service-latest.tar
  
  log "Docker build artifacts:"
  ls -lh snowflake-id-service-latest.tar
  
  echo
}

main() {
  docker_build
}

main "$@"