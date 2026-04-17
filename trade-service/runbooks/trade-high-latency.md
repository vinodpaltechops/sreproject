# Runbook: TradeServiceHighLatencyP99

**Alert:** TradeServiceHighLatencyP99 | **Severity:** Critical

## Triage
```bash
# Check latency percentiles
curl http://<POD_IP>:8080/actuator/metrics/trade.processing.duration

# Check for SLOW symbol chaos test running
kubectl logs -l app=trade-service -n trade-app --since=5m | grep '"symbol":"SLOW"'

# JVM memory pressure
curl http://<POD_IP>:8080/actuator/metrics/jvm.memory.used
```

## Remediation
- **GC pressure** → increase memory limits in deployment.yaml
- **Thread pool saturated** → tune server.tomcat.threads.max in application.yml
- **Downstream slow** → check circuit breaker, force open if needed
