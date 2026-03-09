#!/usr/bin/env bash
set -euo pipefail

# Determine the project root based on the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")

# Set up logging relative to the project root
LOG_FILE="$PROJECT_ROOT/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[stage][docker-publish] %s\n' "$*"
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

docker_publish() {
  log "========================================"
  log "Stage: docker-publish"
  log "========================================"
  
  ensure_cmd docker
  
  log "Starting Docker daemon"
  start_docker_if_needed

  log "Logging in to Docker Hub"
  "$SCRIPT_DIR/docker-login.sh"

  # Load environment variables from local.env at the project root
  if [ -f "$PROJECT_ROOT/local.env" ]; then
    log "Loading environment variables from $PROJECT_ROOT/local.env"
    source "$PROJECT_ROOT/local.env"
  fi

  # Check if DOCKERHUB_USERNAME is set
  if [ -z "${DOCKERHUB_USERNAME:-}" ]; then
    log "Error: DOCKERHUB_USERNAME environment variable is not set"
    log "Please set it with: export DOCKERHUB_USERNAME=your-username"
    log "Or create a local.env file with: export DOCKERHUB_USERNAME=your-username"
    exit 1
  fi

  log "Tagging existing image for Docker Hub"
  docker tag snowflake-id-service:latest "${DOCKERHUB_USERNAME}/snowflake-id-service:latest"

  
  log "Pushing image to Docker Hub"
  docker push "${DOCKERHUB_USERNAME}/snowflake-id-service:latest"
  
  log "Docker image published successfully!"
  log "Image available at: ${DOCKERHUB_USERNAME}/snowflake-id-service:latest"
  
  echo
}

main() {
  docker_publish
}

main "$@"
