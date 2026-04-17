package com.srelab.tradeservice.controller;

import com.srelab.tradeservice.model.Trade;
import com.srelab.tradeservice.model.TradeRequest;
import com.srelab.tradeservice.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /**
     * Submit a new trade. Instrumented with:
     * - Micrometer metrics (via TradeService)
     * - OTel distributed trace (trace-id in response headers)
     */
    @PostMapping
    public ResponseEntity<Trade> createTrade(@Valid @RequestBody TradeRequest request) {
        log.info("POST /api/trades symbol={} side={}", request.getSymbol(), request.getSide());
        Trade trade = tradeService.executeTrade(request);
        HttpStatus status = "REJECTED".equals(trade.getStatus())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(trade);
    }

    @GetMapping("/{tradeId}")
    public ResponseEntity<Trade> getTrade(@PathVariable String tradeId) {
        return ResponseEntity.ok(tradeService.getTrade(tradeId));
    }

    @GetMapping
    public ResponseEntity<Map<String, Trade>> getAllTrades() {
        return ResponseEntity.ok(tradeService.getAllTrades());
    }

    /**
     * SRE diagnostic endpoint — called from runbook triage steps.
     * Returns live stats without hitting downstream systems.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(tradeService.getServiceStats());
    }

    /**
     * Chaos endpoint — used to manually trigger error scenarios for SRE drills.
     * Use symbol=SLOW to trigger latency alerts, symbol=ERROR to trigger error rate alerts.
     */
    @PostMapping("/simulate/{scenario}")
    public ResponseEntity<String> simulate(@PathVariable String scenario) {
        TradeRequest req = new TradeRequest();
        req.setSymbol(scenario.toUpperCase());
        req.setSide("BUY");
        req.setQuantity(new java.math.BigDecimal("100"));
        req.setPrice(new java.math.BigDecimal("50.00"));
        try {
            tradeService.executeTrade(req);
            return ResponseEntity.ok("Scenario '" + scenario + "' executed");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Scenario triggered error: " + e.getMessage());
        }
    }
}
