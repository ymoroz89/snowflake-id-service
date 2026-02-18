#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[local-ci] %s\n' "$*"
}

main() {
  log "========================================"
  log "Starting local ci-cd pipeline"
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

  log "Running deploy stage"
  ./ci-cd/stage-deploy.sh
  echo

  log "Running loadtest stage"
  ./ci-cd/stage-loadtest.sh
  echo

  # Add cluster cleanup at the end
  log "Running cleanup stage"
  ./ci-cd/stage-cleanup.sh
  echo

  log "========================================"
  log "All stages completed successfully!"
  log "========================================"
}

main "$@"
