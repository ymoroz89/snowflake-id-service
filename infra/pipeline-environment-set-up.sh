#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/infra/infra.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[infra] %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

configure_helm_repositories() {
  log "Configuring Helm repositories"
  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
  helm repo add grafana https://grafana.github.io/helm-charts --force-update
  helm repo update
}

install_loki() {
  ./infra/install-loki.sh
}

install_observability_stack() {
  local grafana_admin_password="${GRAFANA_ADMIN_PASSWORD:-admin}"

  log "Installing kube-prometheus-stack (Prometheus + Grafana)"
  helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    --create-namespace \
    -f helm/kube-prometheus-stack/values.yaml \
    --set grafana.adminPassword="${grafana_admin_password}" \
    --wait \
    --timeout 8m
}

install_log_collector() {
  log "Installing Grafana Alloy log collector"
  helm upgrade --install alloy-logs grafana/alloy \
    --namespace monitoring \
    --create-namespace \
    -f helm/alloy/values.yaml \
    --wait \
    --timeout 5m
}

install_ingress_controller() {
  log "Installing ingress-nginx controller"
  helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx \
    --create-namespace \
    -f helm/ingress-nginx/values.yaml \
    --wait \
    --timeout 5m
}

set_up_kind_cluster() {
  ensure_cmd helm
  ensure_cmd docker
  ensure_cmd kubectl
  
  log "Ensuring Kind cluster exists"
  ./infra/kind-cluster.sh create
  
  # Load environment variables from local.env
  if [ -f "local.env" ]; then
    log "Loading environment variables from local.env"
    source local.env
  else
    log "Warning: local.env file not found"
  fi
  
  # Create TLS secret for ingress
  ./infra/create-tls-secret.sh

  configure_helm_repositories
  
  install_ingress_controller
  
  install_loki

  install_observability_stack

  install_log_collector

  echo
}

main() {
    log "========================================"
    log "Starting local deployment pipeline"
    log "========================================"
    echo
  set_up_kind_cluster
    log "========================================"
    log "All stages completed successfully!"
    log "========================================"
}

main "$@"
