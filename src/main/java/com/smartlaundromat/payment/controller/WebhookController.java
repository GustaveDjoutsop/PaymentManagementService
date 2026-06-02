package com.smartlaundromat.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlaundromat.payment.config.PaymentConfig;
import com.smartlaundromat.payment.dto.WebhookPayload;
import com.smartlaundromat.payment.eqlink.EqLinkProperties;
import com.smartlaundromat.payment.eqlink.dto.EqWebhookEvent;
import com.smartlaundromat.payment.model.Transaction;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.service.PaymentService;
import com.smartlaundromat.payment.service.TopUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives payment provider callbacks (CamPay, MTN, Orange Money, EQLink).
 *
 * <p>All endpoints are <strong>public</strong> (no Bearer token) and are
 * secured via HMAC signature verification inside each handler.
 */
@RestController
@RequestMapping("/api/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;
    private final TopUpService topUpService;
    private final PaymentConfig paymentConfig;
    private final EqLinkProperties eqLinkProperties;
    private final ObjectMapper objectMapper;

    // ── CamPay ────────────────────────────────────────────────────────────────

    @PostMapping("/campay")
    public ResponseEntity<Map<String, String>> handleCampayWebhook(
            @RequestHeader(value = "X-Campay-Signature", required = false) String signature,
            @RequestBody WebhookPayload payload) {

        log.info("CamPay webhook received: ref={}, status={}", payload.getExternalReference(), payload.getStatus());

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

    // ── EQLink ────────────────────────────────────────────────────────────────

    /**
     * Receives EQLink payment / cycle events.
     *
     * <p>EQLink pushes events such as {@code payment.confirmed}, {@code cycle.completed},
     * and {@code payment.failed}. These are mapped to our internal transaction model.
     *
     * <p>Signature verification is performed when {@code eqlink.webhook-secret} is configured.
     * Register this URL in the EQLink dashboard:
     * {@code https://your-host/api/webhook/eqlink}
     */
    @PostMapping("/eqlink")
    public ResponseEntity<Map<String, String>> handleEqLinkWebhook(
            @RequestHeader(value = "X-EQLink-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        log.info("EQLink webhook received");

        // Verify signature when a secret is configured
        if (StringUtils.hasText(eqLinkProperties.getWebhookSecret())) {
            if (!isValidHmac(rawBody, signature, eqLinkProperties.getWebhookSecret())) {
                log.warn("Invalid EQLink webhook signature — rejecting");
                return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
            }
        }

        try {
            EqWebhookEvent event = objectMapper.readValue(rawBody, EqWebhookEvent.class);
            log.info("EQLink event: type={}, device={}, txRef={}",
                    event.getEventType(), event.getDeviceId(), event.getTransactionRef());

            processEqLinkEvent(event);
        } catch (Exception e) {
            log.error("Failed to parse EQLink webhook: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Parse error"));
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ── EQLink event routing ──────────────────────────────────────────────────

    private void processEqLinkEvent(EqWebhookEvent event) {
        if (event.getTransactionRef() == null || event.getTransactionRef().isBlank()) {
            log.debug("EQLink event has no transaction_ref — cannot reconcile with local record");
            return;
        }

        String status = switch (event.getEventType() != null ? event.getEventType() : "") {
            case "payment.confirmed", "cycle.completed" -> "SUCCESSFUL";
            case "payment.failed"                       -> "FAILED";
            default                                     -> null;
        };

        if (status == null) {
            log.debug("EQLink event type '{}' not mapped — ignoring", event.getEventType());
            return;
        }

        // amount from EQLink may differ — we record the providerReference (device_id)
        String providerRef = event.getDeviceId();
        String failureReason = "FAILED".equals(status) ? "EQLink payment failed" : null;

        paymentService.processWebhook(
                PaymentProvider.EQLINK,
                event.getTransactionRef(),
                status,
                providerRef,
                failureReason
        );
    }

    // ── Signature verification ────────────────────────────────────────────────

    private boolean isValidHmac(String body, String receivedSig, String secret) {
        if (!StringUtils.hasText(receivedSig)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : computed) hex.append(String.format("%02x", b));
            return MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.UTF_8),
                    receivedSig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }
}
