package com.smartlaundromat.payment.dto;

import lombok.Data;
import java.util.Map;

@Data
public class WebhookPayload {

    private String reference;
    private String externalReference;
    private String status;
    private String amount;
    private String financialTransactionId;
    private String reason;
    private Map<String, Object> additionalData;
}
