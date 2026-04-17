package com.srelab.tradeservice;

import com.srelab.tradeservice.model.TradeRequest;
import com.srelab.tradeservice.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeServiceApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TradeService tradeService;

    @Test
    void contextLoads() {
        assertThat(tradeService).isNotNull();
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void prometheusEndpointExposesMetrics() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("trades_total");
        assertThat(response.getBody()).contains("trade_processing_duration_seconds");
    }

    @Test
    void createTradeReturns201() {
        TradeRequest req = new TradeRequest();
        req.setSymbol("AAPL");
        req.setSide("BUY");
        req.setQuantity(new BigDecimal("10"));
        req.setPrice(new BigDecimal("175.50"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/trades", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("EXECUTED");
        assertThat(response.getBody()).contains("AAPL");
    }

    @Test
    void invalidTradeReturns400() {
        TradeRequest req = new TradeRequest();
        req.setSymbol("");  // invalid
        req.setSide("INVALID");
        req.setQuantity(new BigDecimal("-1"));
        req.setPrice(new BigDecimal("100"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/trades", req, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void statsEndpointReturnsData() {
        ResponseEntity<String> response = restTemplate
                .getForEntity("http://localhost:" + port + "/api/trades/stats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalTrades");
    }
}
