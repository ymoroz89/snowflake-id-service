#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[test] %s\n' "$*"
}

test() {
  log "========================================"
  log "Stage: test"
  log "========================================"
  
  log "Running Gradle tests"
  ./gradlew test
  
  echo
}

main() {
  test
}

main "$@"