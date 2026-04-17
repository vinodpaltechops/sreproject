package com.srelab.tradeservice.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ApiError {
    private int status;
    private String error;
    private List<String> messages;
    private Instant timestamp;
}
