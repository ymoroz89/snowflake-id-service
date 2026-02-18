#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[local-ci] %s\n' "$*"
}

main() {
  log "========================================"
  log "Starting local ci-cd pipeline"
  log "========================================"
  echo

  log "Running build stage"
  ./scripts/stage-build.sh
  echo

  log "Running test stage"
  ./scripts/stage-test.sh
  echo

  log "Running docker-build stage"
  ./scripts/stage-docker-build.sh
  echo

  log "Running docker-publish stage"
  ./scripts/stage-docker-publish.sh
  echo

  log "Running deploy stage"
  ./scripts/stage-deploy.sh
  echo

  log "Running loadtest stage"
  ./scripts/stage-loadtest.sh
  echo

  log "========================================"
  log "All stages completed successfully!"
  log "========================================"
}

main "$@"
