package com.smartlaundromat.payment.service;

import com.smartlaundromat.payment.dto.PaymentInitiationRequest;
import com.smartlaundromat.payment.dto.PaymentResponse;
import com.smartlaundromat.payment.exception.PaymentException;
import com.smartlaundromat.payment.model.Transaction;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.model.enums.PaymentStatus;
import com.smartlaundromat.payment.repository.TransactionRepository;
import com.smartlaundromat.payment.service.provider.CampayService;
import com.smartlaundromat.payment.service.provider.MtnMomoService;
import com.smartlaundromat.payment.service.provider.OrangeMoneyService;
import com.smartlaundromat.payment.service.provider.PaymentProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final CampayService campayService;
    private final MtnMomoService mtnMomoService;
    private final OrangeMoneyService orangeMoneyService;

    @Transactional
    public PaymentResponse initiatePayment(PaymentInitiationRequest request) {
        List<Transaction> activeCycles = transactionRepository
                .findByMachineIdAndStatus(request.getMachineId(), PaymentStatus.SUCCESSFUL);
        if (!activeCycles.isEmpty()) {
            throw new PaymentException("MACHINE_BUSY", "Machine " + request.getMachineId() + " has an active cycle");
        }

        List<Transaction> pendingPayments = transactionRepository
                .findByMachineIdAndStatus(request.getMachineId(), PaymentStatus.PENDING);
        if (!pendingPayments.isEmpty()) {
            throw new PaymentException("PENDING_PAYMENT", "Machine " + request.getMachineId() + " has a pending payment");
        }

        String externalReference = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .externalReference(externalReference)
                .amount(request.getAmount())
                .phoneNumber(request.getPhoneNumber())
                .machineId(request.getMachineId())
                .pulseCount(request.getPulseCount())
                .cycleDuration(request.getCycleDuration())
                .description(request.getDescription())
                .paymentProvider(request.getProvider())
                .build();
        transactionRepository.save(transaction);

        PaymentProviderService provider = resolveProvider(request.getProvider());

        PaymentResponse response = provider.requestPayment(
                request.getPhoneNumber(),
                request.getAmount(),
                request.getDescription(),
                externalReference
        );

        transaction.setProviderReference(response.getProviderReference());
        transactionRepository.save(transaction);

        return response;
    }

    @Transactional
    public Transaction processWebhook(PaymentProvider provider, String externalReference, String status,
                                       String providerReference, String failureReason) {
        Transaction transaction = transactionRepository.findByExternalReference(externalReference)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + externalReference));

        if (transaction.getStatus() == PaymentStatus.SUCCESSFUL) {
            log.info("Transaction already successful, skipping: {}", externalReference);
            return transaction;
        }

        if ("SUCCESSFUL".equalsIgnoreCase(status)) {
            transaction.setStatus(PaymentStatus.SUCCESSFUL);
            transaction.setProviderReference(providerReference);
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason(failureReason);
        }

        return transactionRepository.save(transaction);
    }

    public Transaction getTransactionByReference(String externalReference) {
        return transactionRepository.findByExternalReference(externalReference)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + externalReference));
    }

    public List<Transaction> getTransactionsByMachine(String machineId) {
        return transactionRepository.findByMachineIdOrderByCreatedAtDesc(machineId);
    }

    public List<Transaction> getTransactionsByCard(String cardUid) {
        return transactionRepository.findByRfidCardUidOrderByCreatedAtDesc(cardUid);
    }

    public Map<String, Object> getProviderStatus() {
        return Map.of(
                "campay", Map.of("configured", campayService.isConfigured()),
                "mtn", Map.of("configured", mtnMomoService.isConfigured()),
                "orange_money", Map.of("configured", orangeMoneyService.isConfigured())
        );
    }

    private PaymentProviderService resolveProvider(PaymentProvider provider) {
        return switch (provider) {
            case CAMPAY -> campayService;
            case MTN -> mtnMomoService;
            case ORANGE_MONEY -> orangeMoneyService;
        };
    }
}
