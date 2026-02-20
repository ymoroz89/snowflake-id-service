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

ensure_kind_cluster() {
  local config_file="${1:-k8s/kind-config.yaml}"
  local cluster_name
  cluster_name=$(grep "^name:" "$config_file" | awk '{print $2}')

  if [ -z "$cluster_name" ]; then
    log "Could not determine cluster name from $config_file"
    exit 1
  fi

  if kind get clusters | grep -q "^${cluster_name}$"; then
    log "Kind cluster '${cluster_name}' already exists"
  else
    log "Creating kind cluster '${cluster_name}' from $config_file"
    kind create cluster --config "$config_file"
    log "Kind cluster '${cluster_name}' created successfully"
  fi
}

deploy() {
  log "========================================"
  log "Stage: deploy"
  log "========================================"
  
  ensure_cmd kind
  ensure_cmd helm
  
  log "Ensuring Kind cluster exists"
  ensure_kind_cluster k8s/kind-config.yaml
  
  log "Loading Docker image into Kind cluster"
  kind load docker-image snowflake-id-service:latest --name dev-cluster
  
  log "Deploying to Kubernetes with Helm"
  helm upgrade --install snowflake-id-service ./helm/snowflake-id-service \
    --namespace default \
    --set image.repository=snowflake-id-service \
    --set image.tag=latest \
    --set image.pullPolicy=Never \
    --wait \
    --timeout 5m
  
  log "Deployment completed"
  
  echo
}

main() {
  deploy
}

main "$@"