package com.srelab.tradeservice.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class Trade {
    private String tradeId;
    private String symbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal price;
    private String status;
    private Instant createdAt;
    private Instant executedAt;
    private long processingTimeMs;
}
