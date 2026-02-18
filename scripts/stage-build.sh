#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[build] %s\n' "$*"
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