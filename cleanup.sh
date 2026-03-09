#!/usr/bin/env bash
set -euo pipefail

# Determine the project root based on the script's location
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT="$SCRIPT_DIR"

# Set up logging relative to the project root
LOG_FILE="$PROJECT_ROOT/ci-cd/ci-cd.log"
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
  "$PROJECT_ROOT/infra/kind-cluster.sh" delete
  
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

delete_certs_directory() {
  log "========================================"
  log "Stage: cleanup - Certificates"
  log "========================================"

  local certs_dir="$PROJECT_ROOT/certs"

  if [ -d "$certs_dir" ]; then
    log "Found certificates directory at: $certs_dir"
    echo "Do you want to delete this directory? (y/N)"
    read -r response

    case "$response" in
      [yY]|[yY][eE][sS])
        log "Deleting certificates directory"
        rm -rf "$certs_dir"
        log "Successfully deleted certificates directory"
        ;;
      *)
        log "Skipping certificates directory deletion as requested"
        ;;
    esac
  else
    log "Certificates directory not found, skipping"
  fi

  echo
}

delete_logs() {
  log "========================================"
  log "Stage: cleanup - Logs"
  log "========================================"

  local log_files=("$PROJECT_ROOT/ci-cd/ci-cd.log" "$PROJECT_ROOT/infra/infra.log")
  local found_logs=()

  for file in "${log_files[@]}"; do
    if [ -f "$file" ]; then
      found_logs+=("$file")
    fi
  done

  if [ ${#found_logs[@]} -gt 0 ]; then
    log "Found log files to clean up:"
    for file in "${found_logs[@]}"; do
      log "  - $file"
    done

    echo
    echo "Do you want to delete these log files? (y/N)"
    read -r response

    case "$response" in
      [yY]|[yY][eE][sS])
        log "Deleting log files"
        for file in "${found_logs[@]}"; do
          rm -f "$file"
          log "Successfully deleted log file: $file"
        done
        log "Log file cleanup completed"
        ;;
      *)
        log "Skipping log file deletion as requested"
        ;;
    esac
  else
    log "No log files found to delete, skipping"
  fi
  
  echo
}

main() {
  delete_kind_cluster
  delete_docker_images
  delete_certs_directory
  delete_logs
}

main "$@"
