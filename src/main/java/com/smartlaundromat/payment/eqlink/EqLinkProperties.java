package com.smartlaundromat.payment.eqlink;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for EQLink cloud integration in the PaymentManagementService.
 *
 * <p>When {@code eqlink.enabled=true} the service:
 * <ul>
 *   <li>Accepts EQLink payment webhooks on {@code POST /api/webhook/eqlink} and
 *       records them as {@code EQLINK} provider transactions.</li>
 *   <li>After a successful CamPay/MTN/Orange payment, automatically triggers the
 *       machine start via MachineStateService (which in turn calls EQLink if configured
 *       there).</li>
 * </ul>
 *
 * <p>Example {@code ci/dev.yaml}:
 * <pre>{@code
 * eqlink:
 *   enabled: true
 *   webhook-secret: YOUR_EQLINK_WEBHOOK_SECRET
 *   auto-start-machine-after-payment: true
 * }</pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "eqlink")
public class EqLinkProperties {

    /** Master switch — enables EQLink webhook processing and auto-start. */
    private boolean enabled = false;

    /** EQLink REST API base URL. */
    private String baseUrl = "https://api.eqlink.top";

    /**
     * API key for calling EQLink's REST API.
     * Only needed if PaymentManagementService calls EQLink directly.
     */
    private String apiKey;

    /**
     * HMAC secret for verifying EQLink webhook signatures.
     * Must match the value configured in the EQLink dashboard.
     */
    private String webhookSecret;

    /**
     * When {@code true}, PaymentManagementService automatically POSTs to
     * MachineStateService ({@code /api/machines/start-cycle}) after any
     * payment (CamPay, MTN, Orange, or EQLink) is confirmed as SUCCESSFUL.
     *
     * <p>Requires {@code machine-state-service.base-url} to be configured.
     */
    private boolean autoStartMachineAfterPayment = false;
}
