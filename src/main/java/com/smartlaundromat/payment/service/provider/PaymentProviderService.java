package com.smartlaundromat.payment.service.provider;

import com.smartlaundromat.payment.dto.PaymentResponse;
import java.math.BigDecimal;

public interface PaymentProviderService {

    PaymentResponse requestPayment(String phoneNumber, BigDecimal amount, String description, String externalReference);

    String getProviderName();

    boolean isConfigured();
}
