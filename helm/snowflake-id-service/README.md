# Snowflake ID Service Helm Chart

A Helm chart for deploying the Snowflake ID Service on Kubernetes. This service generates unique distributed IDs using the Snowflake algorithm.

## Architecture Overview

The Snowflake ID Service is deployed as a stateful application with the following architecture:

### Port Flow: HTTPS 443 to Application

```
External Client (HTTPS:443)
    ↓ [TLS Termination at Nginx Ingress]
Nginx Ingress Controller
    ↓ [GRPC Protocol - No TLS inside cluster]
Service snowflake-id-service:9090 (NodePort:30090)
    ↓ [Kubernetes Service Load Balancing]
    ├── Pod snowflake-id-service-0:9090
    ├── Pod snowflake-id-service-1:9090
    └── [Additional pods if scaled]
    ↓ [Container Network Interface]
GRPC Server (Port:9090) + Health Endpoint (Port:9091)
    ↓ [Application Logic]
State File: /data/snowflake.state (Persistent Volume)
```

### Key Components

1. **Ingress Controller**: Nginx with TLS termination on port 443
2. **Service Layer**: NodePort service for external access and internal load balancing
3. **Headless Service**: For StatefulSet pod discovery and stateful operations
4. **StatefulSet**: Ensures stable pod identities for stateful service operations
5. **Persistent Storage**: For maintaining snowflake ID state across pod restarts

## Prerequisites

- Kubernetes 1.35+
- Helm 3.0+

## Installation

### Add the chart repository (if applicable)
```bash
helm repo add your-repo <repository-url>
helm repo update
```

### Install the chart
```bash
helm install my-snowflake-service ./helm/snowflake-id-service
```

### Install with custom values
```bash
helm install my-snowflake-service ./helm/snowflake-id-service -f custom-values.yaml
```

## Configuration

The following table lists the configurable parameters and their default values:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `replicaCount` | Number of replicas | `2` |
| `image.repository` | Image repository | `ymoroz/snowflake-id-service` |
| `image.tag` | Image tag | `latest` |
| `image.pullPolicy` | Image pull policy | `IfNotPresent` |
| `service.type` | Kubernetes service type | `NodePort` |
| `service.port` | Service port | `9090` |
| `service.targetPort` | Service target port | `9090` |
| `service.nodePort` | Service node port | `30090` |
| `ingress.enabled` | Enable ingress | `true` |
| `ingress.className` | Ingress class | `nginx` |
| `ingress.annotations.nginx.ingress.kubernetes.io/backend-protocol` | NGINX upstream protocol | `GRPC` |
| `ingress.hosts` | Ingress hosts | `snowflake-id-service.local` |
| `ingress.tls[0].hosts[0]` | TLS host | `localhost` |
| `ingress.tls[0].secretName` | TLS secret name | `snowflake-id-service-tls` |
| `resources.limits.cpu` | CPU limit | `500m` |
| `resources.limits.memory` | Memory limit | `512Mi` |
| `resources.requests.cpu` | CPU request | `100m` |
| `resources.requests.memory` | Memory request | `128Mi` |
| `autoscaling.enabled` | Enable autoscaling | `true` |
| `autoscaling.minReplicas` | Minimum replicas | `2` |
| `autoscaling.maxReplicas` | Maximum replicas | `100` |
| `autoscaling.targetCPUUtilizationPercentage` | Target CPU utilization | `70` |
| `autoscaling.targetMemoryUtilizationPercentage` | Target memory utilization | `80` |
| `app.metricsPort` | HTTP metrics port exposed by Spring Boot Actuator | `8080` |
| `observability.metricsService.enabled` | Create dedicated ClusterIP service for metrics | `true` |
| `observability.metricsService.port` | Metrics service port | `8080` |
| `observability.prometheus.serviceMonitor.enabled` | Create ServiceMonitor resource | `false` |
| `observability.prometheus.serviceMonitor.path` | Prometheus scrape path | `/actuator/prometheus` |
| `observability.prometheus.serviceMonitor.interval` | Prometheus scrape interval | `15s` |
| `observability.prometheus.serviceMonitor.labels.release` | Label used by kube-prometheus-stack selector | `kube-prometheus-stack` |

## Observability

The application publishes metrics at:

- `http://<pod-ip>:8080/actuator/prometheus`

Enable Prometheus Operator scraping:

```bash
helm upgrade --install my-snowflake-service ./helm/snowflake-id-service \
  --set observability.prometheus.serviceMonitor.enabled=true
```

## Network Configuration Details

### Service Layer (NodePort:30090 → Port:9090)

**Service Configuration:**
- **Service Name**: `snowflake-id-service`
- **Service Type**: `NodePort` - exposes the service on a static port on each worker node
- **NodePort**: `30090` - static port exposed on every node in the cluster
- **ClusterIP Port**: `9090` - internal cluster port
- **TargetPort**: `9090` - port on the pods that receives the traffic
- **Protocol**: TCP
- **Selector**: Matches pods with labels from `_helpers.tpl` (app name and instance)

### StatefulSet Pods (Port:9090)

**Pod Configuration:**
- **Container Port**: `9090` (GRPC service)
- **Health Port**: `9091` (separate health check endpoint)
- **Pod Name Pattern**: `snowflake-id-service-0`, `snowflake-id-service-1`, etc.
- **State File**: `/data/snowflake.state` mounted from persistent volume
- **Volume Mounts**: 
  - `/data` - persistent storage for state file
  - `/tmp` - temporary storage (emptyDir)

### Headless Service for Stateful Operations

**Headless Service Configuration:**
- **Service Name**: `snowflake-id-service-headless`
- **ClusterIP**: `None` - creates a headless service for direct pod DNS resolution
- **Purpose**: Enables StatefulSet pods to discover each other by hostname
- **DNS Resolution**: Each pod gets a DNS name like `pod-name.service-name.namespace.svc.cluster.local`

## Usage

The service can be accessed via gRPC at the configured ingress host and port.

### External Access
```bash
# Access via HTTPS (TLS terminated at ingress)
https://your-domain.com:443
```

### Internal Cluster Access
```bash
# Access via service within cluster
snowflake-id-service:9090
```

### Access the service

Once deployed, the service exposes the following endpoint:

- `GET /snowflake/generateId` - Generate a unique Snowflake ID

### Examples

#### Port-forward to access locally
```bash
kubectl port-forward svc/my-snowflake-service 8080:8080
curl http://localhost:8080/snowflake/generateId
```

#### Enable ingress for external gRPC access (ingress-nginx)
```yaml
ingress:
  enabled: true
  className: nginx
  annotations:
    nginx.ingress.kubernetes.io/backend-protocol: GRPC
  hosts:
    - host: snowflake-id-service.local
      paths:
        - path: /
          pathType: Prefix
```

## TLS Configuration

The chart supports TLS termination at the ingress level. You need to:

1. Create a TLS secret with your certificate and private key
2. Configure the ingress to use the TLS secret
3. Ensure your domain points to the ingress controller

### TLS Termination Details
- **External Traffic**: Encrypted via HTTPS on port 443
- **Internal Traffic**: Unencrypted GRPC protocol within the cluster
- **Certificate Secret**: `snowflake-id-service-tls`
- **Backend Protocol**: GRPC (configured in ingress annotations)

## Scaling

#### Manual scaling
```bash
helm upgrade my-snowflake-service ./helm/snowflake-id-service --set replicaCount=3
```

#### Enable HPA (Horizontal Pod Autoscaler)
```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 80
```

## Health Checks

The service includes gRPC health checks on port 9091 for:
- **Liveness probes**: Detects if the application is running
- **Readiness probes**: Determines if the pod is ready to serve traffic
- **Startup probes**: Handles slow-starting applications

## Persistence

The service uses a persistent volume claim for storing state data. The default size is 10Mi.

### State Management
- **State File**: `/data/snowflake.state`
- **Storage Class**: Uses default storage class
- **Access Mode**: ReadWriteOnce (single pod access)
- **Size**: 10Mi (configurable via `persistence.size`)

## Security Features

- **TLS Termination**: All external traffic encrypted via HTTPS
- **API Gateway Only**: No direct pod access, all traffic through Ingress
- **Load Balancing**: Nginx Ingress provides load balancing across pods
- **Security Context**: Non-root user, read-only filesystem
- **Network Isolation**: Proper service mesh routing

## Security

The chart follows security best practices:

- Runs as non-root user (65532)
- Uses read-only root filesystem
- Drops all capabilities
- Uses distroless base image

## Uninstallation

```bash
helm uninstall my-snowflake-service
```

## Troubleshooting

### Check pod status
```bash
kubectl get pods -l app.kubernetes.io/name=snowflake-id-service
```

### Check logs
```bash
kubectl logs -l app.kubernetes.io/name=snowflake-id-service
```

### Test the service
```bash
kubectl run test-pod --image=curlimages/curl --rm -it --restart=Never -- \
  curl http://my-snowflake-service:8080/snowflake/generateId
