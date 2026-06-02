package com.smartlaundromat.payment.service.provider;

import com.smartlaundromat.payment.dto.PaymentResponse;
import com.smartlaundromat.payment.eqlink.EqLinkProperties;
import com.smartlaundromat.payment.exception.PaymentException;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.model.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment provider implementation for EQLink's built-in payment system.
 *
 * <p>EQLink supports its own payment flow: a customer scans the QR code on the
 * machine and pays via the EQLink mobile app (prepaid balance or connected
 * mobile money). Your backend can initiate this flow via the EQLink API, which
 * creates a payment session on the device.
 *
 * <p>The actual payment confirmation arrives asynchronously via the EQLink webhook
 * ({@code POST /api/webhook/eqlink}) with event type {@code payment.confirmed} or
 * {@code cycle.completed}.
 *
 * <p>This service is only active when {@code eqlink.enabled=true}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EqLinkPaymentService implements PaymentProviderService {

    private final EqLinkProperties eqLinkProperties;
    private final RestTemplate restTemplate;

    @Override
    public String getProviderName() {
        return "EQLINK";
    }

    @Override
    public boolean isConfigured() {
        return eqLinkProperties.isEnabled()
                && eqLinkProperties.getApiKey() != null
                && !eqLinkProperties.getApiKey().isBlank();
    }

    /**
     * Initiates an EQLink payment session on the target machine.
     *
     * <p>This creates a pending session in EQLink's system; the customer is notified
     * on the machine's display or via the EQLink app to confirm payment.
     * Confirmation arrives via the EQLink webhook.
     *
     * @param phoneNumber    customer phone (used as payer identifier in EQLink)
     * @param amount         cycle price in XAF
     * @param description    human-readable description (shown on machine screen)
     * @param externalReference our internal reference, sent to EQLink as {@code transaction_ref}
     */
    @Override
    public PaymentResponse requestPayment(String phoneNumber, BigDecimal amount,
                                          String description, String externalReference) {
        if (!isConfigured()) {
            throw new PaymentException("EQLINK_NOT_CONFIGURED",
                    "EQLink payment provider is not configured or not enabled");
        }

        try {
            String url = eqLinkProperties.getBaseUrl() + "/v1/payments/initiate";

            Map<String, Object> body = Map.of(
                    "amount",           amount.toPlainString(),
                    "currency",         "XAF",
                    "customer_phone",   phoneNumber != null ? phoneNumber : "",
                    "description",      description != null ? description : "Smart Laundry",
                    "transaction_ref",  externalReference
            );

            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            String sessionId = response != null ? (String) response.get("session_id") : null;

            log.info("EQLink payment session initiated: ref={}, sessionId={}", externalReference, sessionId);

            return PaymentResponse.builder()
                    .success(true)
                    .externalReference(externalReference)
                    .providerReference(sessionId)
                    .provider(PaymentProvider.EQLINK)
                    .status(PaymentStatus.PENDING)
                    .amount(amount)
                    .message("EQLink payment session created. Customer must confirm on device.")
                    .build();

        } catch (Exception e) {
            log.error("EQLink payment initiation failed: {}", e.getMessage());
            throw new PaymentException("EQLINK_ERROR", "EQLink payment failed: " + e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + eqLinkProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
