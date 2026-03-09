#!/usr/bin/env bash
set -euo pipefail

# Determine the project root based on the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$(dirname "$SCRIPT_DIR") # infra is one level down from project root

# Set up logging relative to the project root
LOG_FILE="$PROJECT_ROOT/infra/infra.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[tls-secret] %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

generate_certificates() {
  local hostname="$1"
  local key_path="$2"
  local crt_path="$3"

  log "Generating new TLS key and certificate for $hostname."
  local temp_dir
  temp_dir=$(mktemp -d)

  pushd "$temp_dir" >/dev/null

  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout tls.key -out tls.crt \
    -subj "/CN=$hostname/O=$hostname" \
    >/dev/null 2>&1

  # Move generated certificates to the final destination
  mv tls.key "$key_path"
  mv tls.crt "$crt_path"

  popd >/dev/null
  rm -rf "$temp_dir"
  log "TLS key and certificate generated and saved."
}

create_tls_secret() {
  log "========================================"
  log "Stage: create-tls-secret"
  log "========================================"
  
  ensure_cmd kubectl
  ensure_cmd openssl
  
  local secret_name="snowflake-id-service-tls"
  local hostname="localhost" # This should ideally be configurable for real deployments
  local certs_dir="$PROJECT_ROOT/certs"
  local tls_key_path="$certs_dir/tls.key"
  local tls_crt_path="$certs_dir/tls.crt"

  # Ensure certs directory exists
  mkdir -p "$certs_dir"

  # Check if TLS files exist locally
  if [ -f "$tls_key_path" ] && [ -f "$tls_crt_path" ]; then
    log "TLS key and certificate already exist at $certs_dir, reusing them."
  else
    generate_certificates "$hostname" "$tls_key_path" "$tls_crt_path"
  fi

  # Check if Kubernetes TLS secret already exists
  log "Checking if Kubernetes TLS secret '$secret_name' already exists"
  if kubectl get secret "$secret_name" >/dev/null 2>&1; then
    log "Kubernetes TLS secret '$secret_name' already exists, skipping creation."
    return 0
  fi

  log "Creating Kubernetes TLS secret '$secret_name' from files in $certs_dir"
  kubectl create secret tls "$secret_name" \
    --cert="$tls_crt_path" \
    --key="$tls_key_path" \
    >/dev/null 2>&1

  log "Kubernetes TLS secret '$secret_name' created successfully."
  
  echo
}

main() {
  create_tls_secret
}

main "$@"
