#!/usr/bin/env bash
set -euo pipefail

# Set up logging
LOG_FILE="/Users/yuriimoroz/Documents/projects/snowflake-id-service/ci-cd/ci-cd.log"
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

log() {
  printf '[cleanup] %s\n' "$*"
}

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Required command not found: $1"
    exit 1
  fi
}

delete_kind_cluster() {
  log "========================================"
  log "Stage: cleanup"
  log "========================================"
  
  ensure_cmd docker
  
  log "Ensuring Kind cluster exists"
  ./ci-cd/kind-cluster.sh delete k8s/kind-config.yaml
  
  echo
}

delete_docker_images() {
  log "========================================"
  log "Stage: cleanup - Docker images"
  log "========================================"
  
  ensure_cmd docker
  
  # Find snowflake-id-service images (without table format for cleaner parsing)
  local snowflake_images
  snowflake_images=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "snowflake-id-service" || true)
  
  # Find kind images (without table format for cleaner parsing)
  local kind_images
  kind_images=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "kindest/node" || true)
  
  # Check if there are any images to clean up
  if [ -n "$snowflake_images" ] || [ -n "$kind_images" ]; then
    # Combine and filter images
    local all_images
    all_images=$(echo -e "${snowflake_images}\n${kind_images}" | grep -v "^$" | sort -u)
    
    log "Found Docker images to clean up:"
    echo "$all_images" | while IFS= read -r image; do
      if [ -n "$image" ]; then
        log "  - $image"
      fi
    done
    
    echo
    echo "Do you want to delete these Docker images? (y/N)"
    read -r response
    
    case "$response" in
      [yY]|[yY][eE][sS])
        log "Deleting Docker images"
        local deleted_count=0
        local skipped_count=0
        
        # Process each image
        for image in $all_images; do
          if [ -n "$image" ]; then
            log "Removing image: $image"
            if docker rmi "$image" 2>/dev/null; then
              deleted_count=$((deleted_count + 1))
              log "Successfully removed image: $image"
            else
              skipped_count=$((skipped_count + 1))
              log "Image $image not found or in use, skipping"
            fi
          fi
        done
        
        if [ $deleted_count -gt 0 ]; then
          log "Successfully deleted $deleted_count Docker image(s)"
        fi
        
        if [ $skipped_count -gt 0 ]; then
          log "Skipped $skipped_count image(s) that could not be removed"
        fi
        
        log "Docker image cleanup completed"
        ;;
      *)
        log "Skipping Docker image deletion as requested"
        ;;
    esac
  else
    log "Nothing to delete, skipped"
  fi
  
  echo
}

main() {
  delete_kind_cluster k8s/kind-config.yaml
  delete_docker_images
}

main "$@"
