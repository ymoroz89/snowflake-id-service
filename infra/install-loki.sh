#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/infra/infra.log"
mkdir -p "$(dirname "$LOG_FILE")"

log() {
  printf '[infra-loki] %s\n' "$*" | tee -a "$LOG_FILE"
}

install_loki() {
  log "Starting Loki installation (initial phase, no --wait)"
  # We install without --wait first because Loki might fail to start before buckets are created
  helm upgrade --install loki grafana/loki \
    --namespace monitoring \
    --create-namespace \
    -f helm/loki/values.yaml \
    --timeout 8m

  # Wait for MinIO to be ready
  log "Waiting for MinIO (loki-minio-0) to be ready..."
  kubectl wait --for=condition=ready pod -l app=minio -n monitoring --timeout=300s

  # Create required S3 buckets in MinIO
  log "Creating required S3 buckets in MinIO"
  # Set alias and create buckets. Use --ignore-existing for mc mb if possible or just ignore errors if buckets already exist.
  # mc mb returns error if bucket exists, so we ignore it.
  kubectl exec -n monitoring loki-minio-0 -- mc alias set myminio http://loki-minio.monitoring.svc:9000 root-user supersecretpassword
  kubectl exec -n monitoring loki-minio-0 -- mc mb myminio/chunks || log "Bucket 'chunks' may already exist"
  kubectl exec -n monitoring loki-minio-0 -- mc mb myminio/ruler || log "Bucket 'ruler' may already exist"

  log "Waiting for Loki components to become ready (final phase, with --wait)"
  # Run helm upgrade again with --wait to ensure everything is fully healthy
  helm upgrade --install loki grafana/loki \
    --namespace monitoring \
    --create-namespace \
    -f helm/loki/values.yaml \
    --wait \
    --timeout 8m
}

install_loki
