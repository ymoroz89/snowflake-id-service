#!/usr/bin/env bash
set -euo pipefail

# Force dot decimal separator for numeric calculations (Docker flags require "2.00", not "2,00").
export LC_ALL=C

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/infra/infra.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[kind-cluster] %s\n' "$*"
}

warn() {
  printf '[kind-cluster] WARNING: %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

get_cluster_name() {
  local config_file="$1"
  local cluster_name
  cluster_name=$(awk '/^name:[[:space:]]*/ {print $2; exit}' "$config_file")

  if [ -z "$cluster_name" ]; then
    log "Could not determine cluster name from $config_file"
    exit 1
  fi

  printf '%s' "$cluster_name"
}

cpu_to_millicores() {
  local cpu_value="$1"
  if [[ "$cpu_value" == *m ]]; then
    printf '%s' "${cpu_value%m}"
  else
    awk -v v="$cpu_value" 'BEGIN {printf "%.0f", v * 1000}'
  fi
}

memory_to_kib() {
  local mem_value="$1"
  case "$mem_value" in
    *Ki) printf '%s' "${mem_value%Ki}" ;;
    *Mi) awk -v v="${mem_value%Mi}" 'BEGIN {printf "%.0f", v * 1024}' ;;
    *Gi) awk -v v="${mem_value%Gi}" 'BEGIN {printf "%.0f", v * 1024 * 1024}' ;;
    *) awk -v v="$mem_value" 'BEGIN {printf "%.0f", v / 1024}' ;;
  esac
}

set_kubelet_reserved_for_node() {
  local node_name="$1"
  local target_cpu_millicores="$2"
  local target_mem_kib="$3"

  local capacity
  local capacity_cpu
  local capacity_mem
  local capacity_cpu_m
  local capacity_mem_kib
  local reserve_cpu_m
  local reserve_mem_kib
  local reserve_mem_mib

  capacity=$(kubectl get node "$node_name" -o jsonpath='{.status.capacity.cpu} {.status.capacity.memory}')
  if [ -z "$capacity" ]; then
    warn "Could not read node capacity for ${node_name}, skipping kube-reserved update"
    return 1
  fi

  capacity_cpu=$(printf '%s' "$capacity" | awk '{print $1}')
  capacity_mem=$(printf '%s' "$capacity" | awk '{print $2}')
  capacity_cpu_m=$(cpu_to_millicores "$capacity_cpu")
  capacity_mem_kib=$(memory_to_kib "$capacity_mem")

  reserve_cpu_m=$((capacity_cpu_m - target_cpu_millicores))
  if [ "$reserve_cpu_m" -lt 0 ]; then
    reserve_cpu_m=0
  fi

  reserve_mem_kib=$((capacity_mem_kib - target_mem_kib))
  if [ "$reserve_mem_kib" -lt 0 ]; then
    reserve_mem_kib=0
  fi
  reserve_mem_mib=$(((reserve_mem_kib + 1023) / 1024))

  log "Setting kube-reserved for ${node_name}: cpu=${reserve_cpu_m}m, memory=${reserve_mem_mib}Mi"
  docker exec "$node_name" sh -lc "printf '%s\n' \"KUBELET_EXTRA_ARGS=--kube-reserved=cpu=${reserve_cpu_m}m,memory=${reserve_mem_mib}Mi\" > /etc/default/kubelet"
}

apply_dynamic_node_resources() {
  local cluster_name="$1"

  local docker_cpu_total
  local docker_mem_total
  docker_cpu_total=$(docker info --format '{{.NCPU}}')
  docker_mem_total=$(docker info --format '{{.MemTotal}}')

  if [ -z "$docker_cpu_total" ] || [ "$docker_cpu_total" -le 0 ]; then
    log "Could not determine Docker CPU capacity"
    return 1
  fi

  if [ -z "$docker_mem_total" ] || [ "$docker_mem_total" -le 0 ]; then
    log "Could not determine Docker memory capacity"
    return 1
  fi

  local -a node_names
  local node_name
  node_names=()
  while IFS= read -r node_name; do
    if [ -n "$node_name" ]; then
      node_names+=("$node_name")
    fi
  done < <(docker ps --filter "label=io.x-k8s.kind.cluster=${cluster_name}" --format '{{.Names}}' | sort)

  if [ "${#node_names[@]}" -eq 0 ]; then
    log "No kind node containers found for cluster '${cluster_name}'"
    return 1
  fi

  local -a control_nodes worker_nodes
  for node_name in "${node_names[@]}"; do
    if [[ "$node_name" == *control-plane* ]]; then
      control_nodes+=("$node_name")
    else
      worker_nodes+=("$node_name")
    fi
  done

  local control_count="${#control_nodes[@]}"
  local worker_count="${#worker_nodes[@]}"
  if [ "$control_count" -eq 0 ]; then
    warn "No explicit control-plane node names found; treating first node as control-plane for weighting"
    control_nodes=("${node_names[0]}")
    worker_nodes=("${node_names[@]:1}")
    control_count="${#control_nodes[@]}"
    worker_count="${#worker_nodes[@]}"
  fi

  local control_cpu_weight="${KIND_CONTROL_CPU_WEIGHT:-2}"
  local worker_cpu_weight="${KIND_WORKER_CPU_WEIGHT:-1.5}"
  local control_mem_weight="${KIND_CONTROL_MEM_WEIGHT:-3}"
  local worker_mem_weight="${KIND_WORKER_MEM_WEIGHT:-2}"

  local total_cpu_weight
  local total_mem_weight
  total_cpu_weight=$(awk -v c="$control_count" -v w="$worker_count" -v cw="$control_cpu_weight" -v ww="$worker_cpu_weight" 'BEGIN {printf "%.6f", (c * cw) + (w * ww)}')
  total_mem_weight=$(awk -v c="$control_count" -v w="$worker_count" -v cw="$control_mem_weight" -v ww="$worker_mem_weight" 'BEGIN {printf "%.6f", (c * cw) + (w * ww)}')

  if ! awk -v v="$total_cpu_weight" 'BEGIN {exit(v > 0 ? 0 : 1)}'; then
    log "Calculated total CPU weight is invalid: $total_cpu_weight"
    return 1
  fi

  if ! awk -v v="$total_mem_weight" 'BEGIN {exit(v > 0 ? 0 : 1)}'; then
    log "Calculated total memory weight is invalid: $total_mem_weight"
    return 1
  fi

  local control_cpus
  local worker_cpus
  local control_mem_bytes
  local worker_mem_bytes
  local control_target_cpu_m
  local worker_target_cpu_m
  local control_target_mem_kib
  local worker_target_mem_kib
  local docker_mem_gib
  local control_mem_gib
  local worker_mem_gib

  control_cpus=$(awk -v total="$docker_cpu_total" -v weight="$control_cpu_weight" -v sum="$total_cpu_weight" 'BEGIN {printf "%.2f", (total * weight) / sum}')
  worker_cpus=$(awk -v total="$docker_cpu_total" -v weight="$worker_cpu_weight" -v sum="$total_cpu_weight" 'BEGIN {printf "%.2f", (total * weight) / sum}')
  control_mem_bytes=$(awk -v total="$docker_mem_total" -v weight="$control_mem_weight" -v sum="$total_mem_weight" 'BEGIN {printf "%.0f", (total * weight) / sum}')
  worker_mem_bytes=$(awk -v total="$docker_mem_total" -v weight="$worker_mem_weight" -v sum="$total_mem_weight" 'BEGIN {printf "%.0f", (total * weight) / sum}')
  control_target_cpu_m=$(awk -v v="$control_cpus" 'BEGIN {printf "%.0f", v * 1000}')
  worker_target_cpu_m=$(awk -v v="$worker_cpus" 'BEGIN {printf "%.0f", v * 1000}')
  control_target_mem_kib=$((control_mem_bytes / 1024))
  worker_target_mem_kib=$((worker_mem_bytes / 1024))

  docker_mem_gib=$(awk -v bytes="$docker_mem_total" 'BEGIN {printf "%.2f", bytes / 1024 / 1024 / 1024}')
  control_mem_gib=$(awk -v bytes="$control_mem_bytes" 'BEGIN {printf "%.2f", bytes / 1024 / 1024 / 1024}')
  worker_mem_gib=$(awk -v bytes="$worker_mem_bytes" 'BEGIN {printf "%.2f", bytes / 1024 / 1024 / 1024}')

  log "Docker capacity detected: CPU=${docker_cpu_total}, Memory=${docker_mem_gib}GiB"
  log "Applying weighted split (control CPU/MEM weights: ${control_cpu_weight}/${control_mem_weight}, worker CPU/MEM weights: ${worker_cpu_weight}/${worker_mem_weight})"
  log "Per control-plane node target: CPU=${control_cpus}, Memory=${control_mem_gib}GiB"
  log "Per worker node target: CPU=${worker_cpus}, Memory=${worker_mem_gib}GiB"

  for node_name in "${control_nodes[@]}"; do
    log "Updating ${node_name} limits"
    docker update --cpus "$control_cpus" --memory "$control_mem_bytes" --memory-swap "$control_mem_bytes" "$node_name" >/dev/null
  done

  for node_name in "${worker_nodes[@]}"; do
    log "Updating ${node_name} limits"
    docker update --cpus "$worker_cpus" --memory "$worker_mem_bytes" --memory-swap "$worker_mem_bytes" "$node_name" >/dev/null
  done

  for node_name in "${control_nodes[@]}"; do
    set_kubelet_reserved_for_node "$node_name" "$control_target_cpu_m" "$control_target_mem_kib"
  done

  for node_name in "${worker_nodes[@]}"; do
    set_kubelet_reserved_for_node "$node_name" "$worker_target_cpu_m" "$worker_target_mem_kib"
  done

  for node_name in "${node_names[@]}"; do
    if ! docker exec "$node_name" bash -lc 'systemctl restart kubelet' >/dev/null 2>&1; then
      warn "Failed to restart kubelet in ${node_name}; node capacity may still show previous values until restart"
    fi
  done

  kubectl wait --for=condition=Ready --timeout=180s nodes --all >/dev/null
  log "Node resources after applying dynamic resource split:"
  kubectl get nodes -o custom-columns=NAME:.metadata.name,CPU_CAP:.status.capacity.cpu,CPU_ALLOC:.status.allocatable.cpu,MEM_CAP:.status.capacity.memory,MEM_ALLOC:.status.allocatable.memory
}

ensure_kind_cluster() {
  local config_file="${1:-infra/kind/kind-config.yaml}"
  local cluster_name
  cluster_name=$(get_cluster_name "$config_file")

  if kind get clusters | grep -q "^${cluster_name}$"; then
    log "Kind cluster '${cluster_name}' already exists"
  else
    log "Creating kind cluster '${cluster_name}' from $config_file"
    kind create cluster --config "$config_file"
    log "Kind cluster '${cluster_name}' created successfully"
  fi

  apply_dynamic_node_resources "$cluster_name"

  # Install Metrics Server for HPA functionality
  install_metrics_server
}

delete_kind_cluster() {
  local config_file="${1:-infra/kind/kind-config.yaml}"
  local cluster_name
  cluster_name=$(get_cluster_name "$config_file")

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
      ensure_cmd docker
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
