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

configure_helm_repositories() {
  log "Configuring Helm repositories"
  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
  helm repo update
}

install_observability_stack() {
  local grafana_admin_password="${GRAFANA_ADMIN_PASSWORD:-admin}"

  log "Installing kube-prometheus-stack (Prometheus + Grafana)"
  helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    --create-namespace \
    -f kube-prometheus-stack/values.yaml \
    --set grafana.adminPassword="${grafana_admin_password}" \
    --wait \
    --timeout 8m
}

deploy() {
  log "========================================"
  log "Stage: deploy"
  log "========================================"
  
  ensure_cmd helm
  ensure_cmd docker
  ensure_cmd kubectl
  
  log "Ensuring Kind cluster exists"
  ./ci-cd/kind-cluster.sh create k8s/kind-config.yaml
  
  # Load environment variables from local.env
  if [ -f "./ci-cd/local.env" ]; then
    log "Loading environment variables from local.env"
    source ./ci-cd/local.env
  else
    log "Warning: local.env file not found"
  fi
  
  # Create TLS secret for ingress
  log "Creating TLS secret for ingress"
  ./ci-cd/create-tls-secret.sh
  
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

  configure_helm_repositories
  
  # Install ingress-nginx controller with high availability
  log "Installing ingress-nginx controller with high availability"
  helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx \
    --create-namespace \
    --set controller.service.type=NodePort \
    --set controller.service.nodePorts.https=30443 \
    --set controller.ingressClassResource.name=nginx \
    --set controller.ingressClass=nginx \
    --set controller.replicaCount=2 \
    --set controller.autoscaling.enabled=true \
    --set controller.autoscaling.minReplicas=2 \
    --set controller.autoscaling.maxReplicas=10 \
    --set controller.autoscaling.targetCPUUtilizationPercentage=70 \
    --set controller.autoscaling.targetMemoryUtilizationPercentage=80 \
    --wait \
    --timeout 5m
  
  install_observability_stack

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
  deploy
}

main "$@"
