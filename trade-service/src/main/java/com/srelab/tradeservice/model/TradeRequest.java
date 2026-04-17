package com.srelab.tradeservice.model;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TradeRequest {
    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Side must be BUY or SELL")
    @Pattern(regexp = "BUY|SELL", message = "Side must be BUY or SELL")
    private String side;

    @NotNull
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull
    @Positive(message = "Price must be positive")
    private BigDecimal price;
}
