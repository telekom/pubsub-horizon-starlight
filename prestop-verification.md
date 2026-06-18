# Starlight - PreStop Hook & Graceful Shutdown Verification

**Jira:** [Pan] Add K8S pre-stop lifecycle hook to starlight and pulsar and ensure graceful shutdown
**Epic:** [All] Prestop Hooks for Critical Services

## Status: Already Implemented

Starlight already has all required configuration for graceful shutdown.

## Configuration

### Helm Chart (`horizon-starlight/templates/deployment.yaml`)

```yaml
lifecycle:
  preStop:
    exec:
      command: ["sleep", "15"]

terminationGracePeriodSeconds: 45
```

### Spring Boot (`application.yaml`)

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: ${STARLIGHT_PUBLISHING_TIMEOUT_MS:30000}ms
```

## Shutdown Sequence

1. Pod receives termination signal
2. **preStop** runs: `sleep 15` gives K8s time to remove pod from service endpoints
3. **SIGTERM** sent to JVM after preStop completes
4. Spring stops accepting new requests, waits up to 30s for in-flight requests to finish
5. After timeout, remaining connections are force-closed
6. JVM exits

## Timing Alignment

```
terminationGracePeriodSeconds (45s) >= preStop (15s) + shutdownPhaseTimeout (30s)
```

No changes required.
