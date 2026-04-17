# Runbook: TradeServiceHighErrorRate

**Alert:** TradeServiceHighErrorRate | **Severity:** Warning→Critical | **Team:** SRE

## Immediate Triage (< 5 min)

### 1. Pod health
```bash
kubectl get pods -n trade-app -l app=trade-service
kubectl describe pod <POD_NAME> -n trade-app
```

### 2. Tail error logs
```bash
kubectl logs -l app=trade-service -n trade-app --since=5m \
  | jq 'select(.level=="ERROR") | {time: .["@timestamp"], msg: .message, trace: .traceId}'
```

### 3. Circuit breaker state
```bash
curl http://<POD_IP>:8080/actuator/health | jq '.components.circuitBreakers'
```

### 4. Resource pressure
```bash
kubectl top pods -n trade-app && kubectl top nodes
```

## Prometheus Forensics
```promql
# Error rate by status code
sum by(status) (rate(http_server_requests_seconds_count{job="trade-service"}[5m]))

# Rejected trades (circuit breaker)
increase(trades_rejected_total{job="trade-service"}[10m])
```

## Remediation
```bash
# Rollback bad canary
kubectl argo rollouts abort trade-service -n trade-app
kubectl argo rollouts undo trade-service -n trade-app

# Scale out
kubectl scale deployment trade-service -n trade-app --replicas=4
```

## Escalation
| Error Rate | Action |
|---|---|
| 1–5% | Monitor #sre-alerts for 10m |
| >5% | Page on-call + rollback immediately |
| All pods down | P1 — page engineering lead |
