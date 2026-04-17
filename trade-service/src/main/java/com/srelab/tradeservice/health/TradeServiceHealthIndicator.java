package com.srelab.tradeservice.health;

import com.srelab.tradeservice.metrics.TradeMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator — shows up in /actuator/health.
 * SRE runbooks use this for deep health checks during incidents.
 */
@Component("tradeEngine")
@RequiredArgsConstructor
public class TradeServiceHealthIndicator implements HealthIndicator {

    private final TradeMetrics metrics;
    private static final int MAX_PENDING = 100;

    @Override
    public Health health() {
        int pending = metrics.getPendingTradesCount().get();

        if (pending > MAX_PENDING) {
            return Health.down()
                    .withDetail("reason", "Pending trade queue saturated")
                    .withDetail("pendingTrades", pending)
                    .withDetail("threshold", MAX_PENDING)
                    .build();
        }

        return Health.up()
                .withDetail("pendingTrades", pending)
                .withDetail("maxPending", MAX_PENDING)
                .withDetail("status", "Trade engine operational")
                .build();
    }
}
