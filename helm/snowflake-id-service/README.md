# Snowflake ID Service Helm Chart

A Helm chart for deploying the Snowflake ID Service on Kubernetes. This service generates unique distributed IDs using the Snowflake algorithm.

## Prerequisites

- Kubernetes 1.16+
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
| `replicaCount` | Number of replicas | `1` |
| `image.repository` | Image repository | `snowflake-id-service` |
| `image.tag` | Image tag | `latest` |
| `image.pullPolicy` | Image pull policy | `IfNotPresent` |
| `service.type` | Kubernetes service type | `ClusterIP` |
| `service.port` | Service port | `8080` |
| `ingress.enabled` | Enable ingress | `false` |
| `ingress.hosts` | Ingress hosts | `snowflake-id-service.local` |
| `resources.limits.cpu` | CPU limit | `500m` |
| `resources.limits.memory` | Memory limit | `512Mi` |
| `resources.requests.cpu` | CPU request | `100m` |
| `resources.requests.memory` | Memory request | `128Mi` |

## Usage

### Access the service

Once deployed, the service exposes the following endpoint:

- `GET /snowflake/generateId` - Generate a unique Snowflake ID

### Examples

#### Port-forward to access locally
```bash
kubectl port-forward svc/my-snowflake-service 8080:8080
curl http://localhost:8080/snowflake/generateId
```

#### Enable ingress for external access
```yaml
ingress:
  enabled: true
  hosts:
    - host: snowflake-id.example.com
      paths:
        - path: /
          pathType: Prefix
```

### Scaling

#### Manual scaling
```bash
helm upgrade my-snowflake-service ./helm/snowflake-id-service --set replicaCount=3
```

#### Enable HPA (Horizontal Pod Autoscaler)
```yaml
autoscaling:
  enabled: true
  minReplicas: 1
  maxReplicas: 10
  targetCPUUtilizationPercentage: 80
```

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
```