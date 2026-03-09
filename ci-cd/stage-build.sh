#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[stage][build] %s\n' "$*"
}

build() {
  log "========================================"
  log "Stage: build"
  log "========================================"
  
  log "Setting up Gradle environment"
  export GRADLE_USER_HOME="$PWD/.gradle"
  export GRADLE_OPTS="-Dorg.gradle.daemon=false"
  
  log "Making gradlew executable"
  chmod +x ./gradlew
  
  log "Running Gradle build"
  ./gradlew clean assemble -x test
  
  echo
}

main() {
  build
}

main "$@"