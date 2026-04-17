# Trade Service — SRE Lab

Spring Boot 3.2 microservice built for the Sr. SRE interview practical.

## Quick Start (Local)

```bash
# Run locally
./mvnw spring-boot:run

# Build jar
./mvnw clean package -DskipTests

# Run tests
./mvnw test
```

## Key Endpoints

| Endpoint | Description |
|---|---|
| `POST /api/trades` | Submit a trade |
| `GET /api/trades/{id}` | Get trade by ID |
| `GET /api/trades/stats` | Live service stats (SRE runbook) |
| `POST /api/trades/simulate/SLOW` | Trigger latency alert scenario |
| `POST /api/trades/simulate/ERROR` | Trigger error rate alert scenario |
| `GET /actuator/health` | Deep health (liveness + readiness + custom) |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint |

## Key Metrics Exposed

| Metric | Type | Description |
|---|---|---|
| `trades_total` | Counter | All trade requests received |
| `trades_success_total` | Counter | Successfully executed trades |
| `trades_failed_total` | Counter | Failed trades (5xx) |
| `trades_rejected_total` | Counter | Rejected by circuit breaker |
| `trades_pending_count` | Gauge | In-flight trades right now |
| `trade_processing_duration_seconds` | Timer | p50/p95/p99 latency |
| `trade_notional_value` | Summary | USD value processed |

## Docker Build

```bash
# Build
docker build -t trade-service:v1.0 .

# Run
docker run -p 8080:8080 -e ENVIRONMENT=local trade-service:v1.0
```

## SRE Chaos Scenarios

```bash
# Trigger high latency (fires TradeServiceHighLatency alert in Grafana)
curl -X POST http://localhost:8080/api/trades/simulate/SLOW

# Trigger error rate spike (fires TradeServiceHighErrorRate alert)
curl -X POST http://localhost:8080/api/trades/simulate/ERROR

# Normal trade
curl -X POST http://localhost:8080/api/trades \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","side":"BUY","quantity":10,"price":175.50}'
```
