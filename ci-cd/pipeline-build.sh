#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[pipeline][build] %s\n' "$*"
}

main() {
  log "========================================"
  log "Starting local build pipeline"
  log "========================================"
  echo

  log "Running build stage"
  ./ci-cd/stage-build.sh
  echo

  log "Running test stage"
  ./ci-cd/stage-test.sh
  echo

  log "Running docker-build stage"
  ./ci-cd/stage-docker-build.sh
  echo

  log "Running docker-publish stage"
  ./ci-cd/stage-docker-publish.sh
  echo

  log "========================================"
  log "All stages completed successfully!"
  log "========================================"
}

main "$@"
