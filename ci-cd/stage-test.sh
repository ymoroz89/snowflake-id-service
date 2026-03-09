#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[stage][test] %s\n' "$*"
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