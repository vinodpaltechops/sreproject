package com.srelab.tradeservice.service;

import com.srelab.tradeservice.metrics.TradeMetrics;
import com.srelab.tradeservice.model.Trade;
import com.srelab.tradeservice.model.TradeRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeMetrics metrics;
    private final Tracer tracer;

    // In-memory store — replace with DB in production
    private final Map<String, Trade> tradeStore = new ConcurrentHashMap<>();

    @CircuitBreaker(name = "tradeExecution", fallbackMethod = "executeFallback")
    @RateLimiter(name = "tradeApi")
    public Trade executeTrade(TradeRequest request) {
        metrics.recordTradeReceived();
        metrics.getPendingTradesCount().incrementAndGet();
        Timer.Sample timerSample = metrics.startTimer();

        try {
            log.info("Executing trade: symbol={} side={} qty={} price={}",
                    request.getSymbol(), request.getSide(),
                    request.getQuantity(), request.getPrice());

            // Add trace attributes for distributed tracing
            var span = tracer.currentSpan();
            if (span != null) {
                span.tag("trade.symbol", request.getSymbol());
                span.tag("trade.side", request.getSide());
            }

            // Simulate processing (replace with real exchange/broker call)
            simulateProcessingDelay(request.getSymbol());

            String tradeId = "TRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Instant now = Instant.now();

            Trade trade = Trade.builder()
                    .tradeId(tradeId)
                    .symbol(request.getSymbol())
                    .side(request.getSide())
                    .quantity(request.getQuantity())
                    .price(request.getPrice())
                    .status("EXECUTED")
                    .createdAt(now)
                    .executedAt(now)
                    .build();

            tradeStore.put(tradeId, trade);

            double notional = request.getPrice().multiply(request.getQuantity()).doubleValue();
            metrics.recordTradeSuccess(notional);

            log.info("Trade executed successfully: tradeId={} notional={}", tradeId, notional);
            return trade;

        } catch (Exception e) {
            metrics.recordTradeFailed();
            log.error("Trade execution failed: symbol={} error={}", request.getSymbol(), e.getMessage(), e);
            throw e;
        } finally {
            metrics.getPendingTradesCount().decrementAndGet();
            metrics.stopTimer(timerSample);
        }
    }

    public Trade fallbackExecute(TradeRequest request, Throwable ex) {
        metrics.recordTradeRejected();
        log.warn("Circuit breaker open — returning rejected trade for symbol={}", request.getSymbol());
        return Trade.builder()
                .tradeId("REJECTED-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .status("REJECTED")
                .createdAt(Instant.now())
                .build();
    }

    // Expose fallback with correct signature for Resilience4j
    private Trade executeFallback(TradeRequest request, Throwable ex) {
        return fallbackExecute(request, ex);
    }

    public Trade getTrade(String tradeId) {
        Trade trade = tradeStore.get(tradeId);
        if (trade == null) {
            throw new IllegalArgumentException("Trade not found: " + tradeId);
        }
        return trade;
    }

    public Map<String, Trade> getAllTrades() {
        return Map.copyOf(tradeStore);
    }

    public Map<String, Object> getServiceStats() {
        return Map.of(
                "totalTrades", tradeStore.size(),
                "pendingTrades", metrics.getPendingTradesCount().get(),
                "executedTrades", tradeStore.values().stream()
                        .filter(t -> "EXECUTED".equals(t.getStatus())).count(),
                "rejectedTrades", tradeStore.values().stream()
                        .filter(t -> "REJECTED".equals(t.getStatus())).count()
        );
    }

    private void simulateProcessingDelay(String symbol) {
        try {
            // Simulate variable latency by symbol (for SRE alerting demos)
            long delay = switch (symbol.toUpperCase()) {
                case "SLOW" -> 3000L;   // triggers latency alerts
                case "ERROR" -> throw new RuntimeException("Simulated downstream failure");
                default -> 50L + (long)(Math.random() * 100);
            };
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
