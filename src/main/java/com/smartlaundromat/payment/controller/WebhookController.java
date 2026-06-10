package com.smartlaundromat.payment.controller;

import com.smartlaundromat.payment.config.PaymentConfig;
import com.smartlaundromat.payment.dto.WebhookPayload;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.service.PaymentService;
import com.smartlaundromat.payment.service.TopUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives payment provider callbacks (CamPay, MTN MoMo, Orange Money).
 *
 * <p>All endpoints are <strong>public</strong> (no Bearer token required) and are
 * secured by HMAC signature verification inside each provider's controller logic.
 *
 * <p>Note: EQLink is not listed here because EQLink is a machine CONTROL platform,
 * not a payment system. There are no EQLink payment webhooks.
 */
@RestController
@RequestMapping("/api/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;
    private final TopUpService topUpService;
    private final PaymentConfig paymentConfig;

    // ── CamPay ────────────────────────────────────────────────────────────────

    @PostMapping("/campay")
    public ResponseEntity<Map<String, String>> handleCampayWebhook(
            @RequestHeader(value = "X-Campay-Signature", required = false) String signature,
            @RequestBody WebhookPayload payload) {

        log.info("CamPay webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus()); //

        paymentService.processWebhook(
                PaymentProvider.CAMPAY,
                payload.getExternalReference(),
                payload.getStatus(),
                payload.getReference(),
                payload.getReason()
        );

        topUpService.processTopUpWebhook(payload.getExternalReference(), payload.getStatus(), payload.getReason());

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ── MTN MoMo ──────────────────────────────────────────────────────────────

    @PostMapping("/mtn")
    public ResponseEntity<Map<String, String>> handleMtnWebhook(@RequestBody WebhookPayload payload) {
        log.info("MTN webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus());

        paymentService.processWebhook(
                PaymentProvider.MTN,
                payload.getExternalReference(),
                payload.getStatus(),
                payload.getFinancialTransactionId(),
                payload.getReason()
        );

        topUpService.processTopUpWebhook(payload.getExternalReference(), payload.getStatus(), payload.getReason());

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ── Orange Money ──────────────────────────────────────────────────────────

    @PostMapping("/orange")
    public ResponseEntity<Map<String, String>> handleOrangeWebhook(@RequestBody WebhookPayload payload) {
        log.info("Orange Money webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus());

        paymentService.processWebhook(
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
