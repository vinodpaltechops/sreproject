package com.srelab.tradeservice.metrics;

import io.micrometer.core.instrument.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central SRE metrics registry for the trade service.
 * Exposes RED metrics (Rate, Errors, Duration) + business KPIs.
 */
@Component
public class TradeMetrics {

    // RED: Rate — total trades by symbol and side
    private final Counter tradesTotal;
    private final Counter tradesSuccessTotal;
    private final Counter tradesFailedTotal;

    // RED: Errors — rejected trades
    private final Counter tradesRejectedTotal;

    // RED: Duration — processing latency with percentiles
    private final Timer tradeProcessingDuration;

    // Business KPI: pending trades (gauge — point-in-time)
    @Getter
    private final AtomicInteger pendingTradesCount = new AtomicInteger(0);

    // Business KPI: notional value processed
    private final DistributionSummary tradeNotionalValue;

    public TradeMetrics(MeterRegistry registry) {
        this.tradesTotal = Counter.builder("trades_total")
                .description("Total number of trade requests received")
                .register(registry);

        this.tradesSuccessTotal = Counter.builder("trades_success_total")
                .description("Total successfully executed trades")
                .register(registry);

        this.tradesFailedTotal = Counter.builder("trades_failed_total")
                .description("Total failed trade requests (5xx errors)")
                .register(registry);

        this.tradesRejectedTotal = Counter.builder("trades_rejected_total")
                .description("Total rejected trades (validation failures, 4xx)")
                .register(registry);

        this.tradeProcessingDuration = Timer.builder("trade_processing_duration_seconds")
                .description("End-to-end trade processing latency")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        Gauge.builder("trades_pending_count", pendingTradesCount, AtomicInteger::get)
                .description("Number of trades currently being processed")
                .register(registry);

        this.tradeNotionalValue = DistributionSummary.builder("trade_notional_value")
                .description("Notional value (price * quantity) of executed trades")
                .baseUnit("USD")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordTradeReceived() { tradesTotal.increment(); }
    public void recordTradeSuccess(double notional) {
        tradesSuccessTotal.increment();
        tradeNotionalValue.record(notional);
    }
    public void recordTradeFailed() { tradesFailedTotal.increment(); }
    public void recordTradeRejected() { tradesRejectedTotal.increment(); }
    public Timer.Sample startTimer() { return Timer.start(); }
    public void stopTimer(Timer.Sample sample) { sample.stop(tradeProcessingDuration); }
}
