package com.smartlaundromat.payment.service.provider;

import com.smartlaundromat.payment.config.PaymentConfig;
import com.smartlaundromat.payment.dto.PaymentResponse;
import com.smartlaundromat.payment.exception.PaymentException;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.model.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CampayService implements PaymentProviderService {

    private final PaymentConfig paymentConfig;
    private final WebClient.Builder webClientBuilder;

    @Override
    public PaymentResponse requestPayment(String phoneNumber, BigDecimal amount, String description, String externalReference) {
        PaymentConfig.CampayConfig config = paymentConfig.getCampay();

        if (!isConfigured()) {
            throw new PaymentException("CAMPAY_NOT_CONFIGURED", "CamPay payment provider is not configured");
        }

        String formattedPhone = formatPhoneNumber(phoneNumber);

        try {
            String token = getAccessToken(config);

            WebClient client = webClientBuilder.baseUrl(config.getBaseUrl()).build();

            Map<String, String> requestBody = Map.of(
                    "amount", amount.toPlainString(),
                    "currency", paymentConfig.getCurrency(),
                    "from", formattedPhone,
                    "description", description != null ? description : "Smart Laundry Payment",
                    "external_reference", externalReference
            );

            Map<?, ?> response = client.post()
                    .uri("/api/collect/")
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String providerRef = response != null ? (String) response.get("reference") : null;

            log.info("CamPay payment initiated: ref={}, providerRef={}", externalReference, providerRef);

            return PaymentResponse.builder()
                    .success(true)
                    .externalReference(externalReference)
                    .providerReference(providerRef)
                    .provider(PaymentProvider.CAMPAY)
                    .status(PaymentStatus.PENDING)
                    .amount(amount)
                    .message("Payment request sent. Please confirm on your phone.")
                    .build();

        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            log.error("CamPay payment failed: {}", e.getMessage(), e);
            throw new PaymentException("CAMPAY_ERROR", mapCampayError(e.getMessage()));
        }
    }

    @Override
    public String getProviderName() {
        return "CAMPAY";
    }

    @Override
    public boolean isConfigured() {
        PaymentConfig.CampayConfig config = paymentConfig.getCampay();
        return config.getAppKey() != null && !config.getAppKey().isBlank()
                && config.getAppSecret() != null && !config.getAppSecret().isBlank();
    }

    private String getAccessToken(PaymentConfig.CampayConfig config) {
        WebClient client = webClientBuilder.baseUrl(config.getBaseUrl()).build();

        Map<?, ?> tokenResponse = client.post()
                .uri("/api/token/")
                .header("Content-Type", "application/json")
                .bodyValue(Map.of(
                        "username", config.getAppKey(),
                        "password", config.getAppSecret()
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse == null || !tokenResponse.containsKey("token")) {
            throw new PaymentException("CAMPAY_AUTH_FAILED", "Failed to authenticate with CamPay");
        }

        return (String) tokenResponse.get("token");
    }

    String formatPhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");

        if (cleaned.startsWith("237") && cleaned.length() == 12) {
            return cleaned;
        }
        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            return "237" + cleaned.substring(1);
        }
        if (cleaned.length() == 9 && cleaned.startsWith("6")) {
            return "237" + cleaned;
        }
        if (cleaned.length() == 8) {
            return "2376" + cleaned;
        }

        return cleaned;
    }

    private String mapCampayError(String errorMessage) {
        if (errorMessage == null) return "Payment failed";
        if (errorMessage.contains("ER102")) return "Only MTN Mobile Money or Orange Money accepted";
        if (errorMessage.contains("ER101")) return "Invalid phone number format";
        if (errorMessage.contains("ER103")) return "Insufficient funds";
        if (errorMessage.contains("ER104")) return "Daily transaction limit exceeded";
        if (errorMessage.contains("ER105")) return "Mobile money account not activated";
        if (errorMessage.contains("ER106")) return "Payment declined";
        return "Payment failed: " + errorMessage;
    }
}
