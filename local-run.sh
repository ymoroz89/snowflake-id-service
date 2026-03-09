#!/usr/bin/env bash
set -euo pipefail

# Determine the project root based on the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT="$SCRIPT_DIR"
CI_CD_DIR="$PROJECT_ROOT/ci-cd"

log() {
  printf '[local-run] %s\n' "$*"
}

log "========================================"
log "Starting local pipeline run"
log "========================================"

# 1. Run pipeline-environment-set-up.sh
log "Running environment setup..."
"$PROJECT_ROOT/infra/pipeline-environment-set-up.sh"
log "Environment setup completed."

# 2. Run pipeline-build.sh
log "Running build stage..."
"$CI_CD_DIR/pipeline-build.sh"
log "Build stage completed."

# 3. Run pipeline-deployment.sh
log "Running deployment stage..."
"$CI_CD_DIR/pipeline-deployment.sh"
log "Deployment stage completed."

# 4. Ask before running pipeline-load-test.sh
echo
log "Do you want to run the load test? (y/N)"
read -r response

case "$response" in
  [yY]|[yY][eE][sS])
    log "Running load test stage..."
    "$CI_CD_DIR/pipeline-load-test.sh"
    log "Load test stage completed."
    ;;
  *)
    log "Skipping load test stage as requested."
    ;;
esac

log "========================================"
log "Local pipeline run finished."
log "========================================"
