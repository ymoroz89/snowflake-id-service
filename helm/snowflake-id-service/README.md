# Snowflake ID Service Helm Chart

Helm chart for deploying the Snowflake gRPC ID service.

Related server architecture doc: [../../snowflake-server/ARCHITECTURE.md](../../snowflake-server/ARCHITECTURE.md)

## Overview

This chart deploys:

- A `StatefulSet` for stable pod identity and per-pod state persistence.
- A gRPC service (`9090`) and a headless service for StatefulSet DNS.
- Optional ingress (enabled by default) with TLS termination and gRPC backend.
- Optional autoscaling (enabled by default) via `HorizontalPodAutoscaler`.
- Metrics service + `ServiceMonitor` (both enabled by default).

## Default Topology

- gRPC API: container port `9090`
- gRPC health endpoint: container port `9091`
- HTTP metrics endpoint: container port `8080` (`/actuator/prometheus`)
- Service type: `NodePort` (`30090` -> `9090`)
- Ingress class: `nginx`
- Ingress host: `localhost`
- TLS secret: `snowflake-id-service-tls`

## Local Infra Integration (`infra/`)

For this repository's local workflow, infra scripts provision the cluster and supporting stacks:

- `infra/pipeline-environment-set-up.sh`
  - Ensures Kind cluster `dev-cluster`
  - Creates/reuses TLS secret `snowflake-id-service-tls`
  - Installs ingress-nginx and kube-prometheus-stack
- `infra/kind/kind-config.yaml` host mappings:
  - `443 -> 30443` (ingress HTTPS)
  - `30090 -> 30090` (service NodePort)
  - `30091 -> 30091` (Prometheus)
  - `30300 -> 30300` (Grafana)
- `infra/create-tls-secret.sh` generates self-signed `localhost` certs under `certs/` when missing.

## Default Values Snapshot

| Parameter | Default |
| --- | --- |
| `replicaCount` | `3` |
| `image.repository` | `snowflake-id-service` |
| `image.pullPolicy` | `Never` |
| `image.tag` | `latest` |
| `service.type` | `NodePort` |
| `service.port` | `9090` |
| `service.nodePort` | `30090` |
| `app.healthPort` | `9091` |
| `app.metricsPort` | `8080` |
| `autoscaling.enabled` | `true` |
| `autoscaling.minReplicas` | `3` |
| `autoscaling.maxReplicas` | `15` |
| `observability.metricsService.enabled` | `true` |
| `observability.prometheus.serviceMonitor.enabled` | `true` |
| `persistence.enabled` | `true` |
| `persistence.size` | `10Mi` |

## Install

```bash
helm upgrade --install snowflake-id-service ./helm/snowflake-id-service
```

Install with custom values:

```bash
helm upgrade --install snowflake-id-service ./helm/snowflake-id-service -f custom-values.yaml
```

## Scale

Manual replica update:

```bash
helm upgrade snowflake-id-service ./helm/snowflake-id-service --set replicaCount=3
```

HPA tuning:

```bash
helm upgrade snowflake-id-service ./helm/snowflake-id-service \
  --set autoscaling.enabled=true \
  --set autoscaling.minReplicas=3 \
  --set autoscaling.maxReplicas=15
```

## Observability

- Metrics endpoint path: `/actuator/prometheus`
- Metrics service name: `<release>-snowflake-id-service-metrics`
- `ServiceMonitor` is created when:
  - `observability.prometheus.serviceMonitor.enabled=true`
  - `ServiceMonitor` CRD exists in the cluster

## Security and Runtime

- Runs as non-root (`runAsUser: 65532`)
- Drops all Linux capabilities
- Read-only root filesystem
- PVC mounted at `/data` for state file storage
- Temporary writable volume mounted at `/tmp`

## Uninstall

```bash
helm uninstall snowflake-id-service
```
