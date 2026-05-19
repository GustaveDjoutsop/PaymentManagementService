package com.smartlaundromat.payment.controller;

import com.smartlaundromat.payment.config.PaymentConfig;
import com.smartlaundromat.payment.dto.WebhookPayload;
import com.smartlaundromat.payment.model.Transaction;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.service.PaymentService;
import com.smartlaundromat.payment.service.TopUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;
    private final TopUpService topUpService;
    private final PaymentConfig paymentConfig;

    @PostMapping("/campay")
    public ResponseEntity<Map<String, String>> handleCampayWebhook(
            @RequestHeader(value = "X-Campay-Signature", required = false) String signature,
            @RequestBody WebhookPayload payload) {

        log.info("CamPay webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus());

        Transaction transaction = paymentService.processWebhook(
                PaymentProvider.CAMPAY,
                payload.getExternalReference(),
                payload.getStatus(),
                payload.getReference(),
                payload.getReason()
        );

        topUpService.processTopUpWebhook(payload.getExternalReference(), payload.getStatus(), payload.getReason());

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @PostMapping("/mtn")
    public ResponseEntity<Map<String, String>> handleMtnWebhook(@RequestBody WebhookPayload payload) {
        log.info("MTN webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus());

        Transaction transaction = paymentService.processWebhook(
                PaymentProvider.MTN,
                payload.getExternalReference(),
                payload.getStatus(),
                payload.getFinancialTransactionId(),
                payload.getReason()
        );

        topUpService.processTopUpWebhook(payload.getExternalReference(), payload.getStatus(), payload.getReason());

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @PostMapping("/orange")
    public ResponseEntity<Map<String, String>> handleOrangeWebhook(@RequestBody WebhookPayload payload) {
        log.info("Orange Money webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus());

        Transaction transaction = paymentService.processWebhook(
                PaymentProvider.ORANGE_MONEY,
                payload.getExternalReference(),
                payload.getStatus(),
                payload.getReference(),
                payload.getReason()
        );

        topUpService.processTopUpWebhook(payload.getExternalReference(), payload.getStatus(), payload.getReason());

        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
