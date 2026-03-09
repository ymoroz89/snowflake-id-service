#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[deploy] %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

deploy() {
  log "========================================"
  log "Stage: deploy"
  log "========================================"
  
  # Check if image exists locally, if not pull it
  if ! docker image inspect snowflake-id-service:latest >/dev/null 2>&1; then
    log "Image snowflake-id-service:latest not found locally, pulling from Docker Hub"
    ./ci-cd/docker-login.sh
    docker pull ${DOCKERHUB_USERNAME}/snowflake-id-service:latest
    docker tag ${DOCKERHUB_USERNAME}/snowflake-id-service:latest snowflake-id-service:latest
  else
    log "Image snowflake-id-service:latest found locally"
  fi
  
  log "Loading Docker image into Kind cluster"
  kind load docker-image snowflake-id-service:latest --name dev-cluster

  # Deploy application with nginx gRPC ingress
  log "Deploying to Kubernetes with Helm"
  helm upgrade --install snowflake-id-service ./helm/snowflake-id-service \
    --namespace default \
    --set observability.prometheus.serviceMonitor.enabled=true \
    --wait \
    --timeout 5m

  log "Deployment completed"
  log "Prometheus URL: http://localhost:30091"
  log "Grafana URL: http://localhost:30300 (admin/${GRAFANA_ADMIN_PASSWORD:-admin})"
  
  echo
}

main() {
    log "========================================"
    log "Starting local deployment pipeline"
    log "========================================"
    echo
  deploy
    log "========================================"
    log "All stages completed successfully!"
    log "========================================"
}

main "$@"
