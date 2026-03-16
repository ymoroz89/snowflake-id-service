#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/infra/infra.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[kind-cluster] %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

ensure_kind_cluster() {
  local config_file="${1:-infra/kind/kind-config.yaml}"
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
  
  # Install Metrics Server for HPA functionality
  install_metrics_server
}

delete_kind_cluster() {
  local config_file="${1:-infra/kind/kind-config.yaml}"
  local cluster_name
  cluster_name=$(grep "^name:" "$config_file" | awk '{print $2}')

  if [ -z "$cluster_name" ]; then
    log "Could not determine cluster name from $config_file"
    exit 1
  fi

  if kind get clusters | grep -q "^${cluster_name}$"; then
    log "Kind cluster '${cluster_name}' exists"
    
    # Ask user for confirmation
    echo
    echo "Do you want to delete the Kind cluster '${cluster_name}'? (y/N)"
    read -r response
    
    case "$response" in
      [yY]|[yY][eE][sS])
        log "Deleting Kind cluster '${cluster_name}'"
        kind delete cluster --name "$cluster_name"
        log "Kind cluster '${cluster_name}' deleted successfully"
        ;;
      *)
        log "Skipping cluster deletion as requested"
        ;;
    esac
  else
    log "Kind cluster '${cluster_name}' does not exist, skipping deletion"
  fi
}

install_metrics_server() {
  log "Installing Metrics Server for HPA functionality"
  
  # Apply Metrics Server components
  log "Applying Metrics Server components"
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
  
  # Patch Metrics Server to allow insecure connections to kubelet
  log "Patching Metrics Server for insecure TLS connections"
  kubectl patch deployment metrics-server -n kube-system --type='json' -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]'

  # Install Metrics Server for HPA functionality
  kubectl patch deployment metrics-server -n kube-system --type='json' -p='[{"op": "add", "path": "/spec/template/spec/tolerations", "value": [{"key": "node-role.kubernetes.io/control-plane", "operator": "Exists", "effect": "NoSchedule"}]}]'
  kubectl patch deployment metrics-server -n kube-system --type='json' -p='[{"op": "add", "path": "/spec/template/spec/affinity", "value": {"podAntiAffinity": {"preferredDuringSchedulingIgnoredDuringExecution": [{"weight": 100, "podAffinityTerm": {"labelSelector": {"matchExpressions": [{"key": "k8s-app", "operator": "In", "values": ["metrics-server"]}]}, "topologyKey": "kubernetes.io/hostname"}}]}}}]'
  
  # Scale Metrics Server to 3 replicas for high availability
  log "Scaling Metrics Server to 3 replicas for high availability"
  kubectl scale deployment metrics-server -n kube-system --replicas=3
  
  # Verify Metrics Server deployment
  log "Verifying Metrics Server deployment"
  kubectl get deployment metrics-server -n kube-system
  
  # Wait for Metrics Server to be ready with enhanced checks
  log "Waiting for Metrics Server to be ready"
  
  # First, wait for the deployment to be available
  kubectl wait --for=condition=available --timeout=400s deployment/metrics-server -n kube-system
  
  # Verify Metrics Server pods are running and ready
  log "Verifying Metrics Server pod status"
  kubectl get pods -n kube-system | grep metrics-server
  
  # Test metrics collection with retry logic
  log "Testing metrics collection"
  local max_attempts=10
  local attempt=1
  local success=false
  
  while [ $attempt -le $max_attempts ]; do
    log "Attempt $attempt/$max_attempts: Testing metrics collection"
    if kubectl top nodes >/dev/null 2>&1; then
      log "Metrics collection test successful"
      success=true
      break
    else
      log "Metrics collection test failed, waiting 10 seconds before retry"
      sleep 10
      attempt=$((attempt + 1))
    fi
  done
  
  if [ "$success" = false ]; then
    log "ERROR: Metrics collection test failed after $max_attempts attempts"
    log "This may indicate that Metrics Server is not properly configured or accessible"
    log "You may need to check the Metrics Server logs or configuration"
    return 1
  fi
  
  log "Metrics Server installation completed successfully with 3 replicas"
}

main() {
  case "${1:-}" in
    "create")
      ensure_cmd kind
      ensure_cmd kubectl
      ensure_kind_cluster "${2:-infra/kind/kind-config.yaml}"
      ;;
    "delete")
      ensure_cmd kind
      delete_kind_cluster "${2:-infra/kind/kind-config.yaml}"
      ;;
    *)
      echo "Usage: $0 {create|delete} [config_file]"
      echo "  create [config_file]  - Create kind cluster from config file (default: infra/kind/kind-config.yaml)"
      echo "  delete [config_file]  - Delete kind cluster from config file (default: infra/kind/kind-config.yaml)"
      exit 1
      ;;
  esac
}

main "$@"