#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
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

create_tls_secret() {
  log "========================================"
  log "Stage: create-tls-secret"
  log "========================================"
  
  ensure_cmd kubectl
  ensure_cmd openssl
  
  local secret_name="snowflake-id-service-tls"
  local hostname="localhost"
  
  log "Checking if TLS secret '$secret_name' already exists"
  if kubectl get secret "$secret_name" >/dev/null 2>&1; then
    log "TLS secret '$secret_name' already exists, skipping creation"
    return 0
  fi
  
  log "Creating TLS certificate and key for $hostname"
  local temp_dir="/tmp/snowflake-tls-$$"
  mkdir -p "$temp_dir"
  
  pushd "$temp_dir" >/dev/null
  
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout tls.key -out tls.crt \
    -subj "/CN=$hostname/O=$hostname" \
    >/dev/null 2>&1
  
  # Create certs directory in project root
  mkdir -p "/Users/yuriimoroz/Documents/projects/snowflake-id-service/certs"
  
  # Copy certificates to project root certs directory
  cp tls.key "/Users/yuriimoroz/Documents/projects/snowflake-id-service/certs/"
  cp tls.crt "/Users/yuriimoroz/Documents/projects/snowflake-id-service/certs/"
  
  log "Creating Kubernetes TLS secret '$secret_name'"
  kubectl create secret tls "$secret_name" \
    --cert=tls.crt \
    --key=tls.key \
    >/dev/null 2>&1
  
  popd >/dev/null
  rm -rf "$temp_dir"
  
  log "TLS secret '$secret_name' created successfully"
  
  echo
}

main() {
  create_tls_secret
}

main "$@"