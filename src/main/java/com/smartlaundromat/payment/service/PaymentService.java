package com.smartlaundromat.payment.service;

import com.smartlaundromat.payment.dto.PaymentInitiationRequest;
import com.smartlaundromat.payment.dto.PaymentResponse;
import com.smartlaundromat.payment.exception.PaymentException;
import com.smartlaundromat.payment.model.Transaction;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.model.enums.PaymentStatus;
import com.smartlaundromat.payment.repository.TransactionRepository;
import com.smartlaundromat.payment.service.machine.MachineStartService;
import com.smartlaundromat.payment.service.provider.CampayService;
import com.smartlaundromat.payment.service.provider.EqLinkPaymentService;
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

    // ── Payment providers ─────────────────────────────────────────────────────
    private final CampayService campayService;
    private final MtnMomoService mtnMomoService;
    private final OrangeMoneyService orangeMoneyService;
    /** EQLink's own payment system — active when eqlink.enabled=true. */
    private final EqLinkPaymentService eqLinkPaymentService;

    /** Triggers MachineStateService after successful payment when auto-start is on. */
    private final MachineStartService machineStartService;

    // ── Payment initiation ────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse initiatePayment(PaymentInitiationRequest request) {
        List<Transaction> activeCycles = transactionRepository
                .findByMachineIdAndStatus(request.getMachineId(), PaymentStatus.SUCCESSFUL);
        if (!activeCycles.isEmpty()) {
            throw new PaymentException("MACHINE_BUSY",
                    "Machine " + request.getMachineId() + " has an active cycle");
        }
        List<Transaction> pendingPayments = transactionRepository
                .findByMachineIdAndStatus(request.getMachineId(), PaymentStatus.PENDING);
        if (!pendingPayments.isEmpty()) {
            throw new PaymentException("PENDING_PAYMENT",
                    "Machine " + request.getMachineId() + " has a pending payment");
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

    // ── Webhook processing ────────────────────────────────────────────────────

    /**
     * Processes a payment provider callback (CamPay, MTN, Orange, or EQLink).
     *
     * <p>After marking a transaction {@code SUCCESSFUL}, automatically triggers
     * MachineStateService to start the machine if
     * {@code eqlink.auto-start-machine-after-payment=true}.
     */
    @Transactional
    public Transaction processWebhook(PaymentProvider provider,
                                      String externalReference,
                                      String status,
                                      String providerReference,
                                      String failureReason) {

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
            transactionRepository.save(transaction);

            log.info("Payment SUCCESSFUL — tx={}, machine={}, provider={}",
                    externalReference, transaction.getMachineId(), provider);

            // Auto-trigger machine start via MachineStateService
            machineStartService.notifyMachineStart(transaction);

        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason(failureReason);
            transactionRepository.save(transaction);
        }

        return transaction;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

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
                "campay",       Map.of("configured", campayService.isConfigured()),
                "mtn",          Map.of("configured", mtnMomoService.isConfigured()),
                "orange_money", Map.of("configured", orangeMoneyService.isConfigured()),
                "eqlink",       Map.of("configured", eqLinkPaymentService.isConfigured())
        );
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private PaymentProviderService resolveProvider(PaymentProvider provider) {
        return switch (provider) {
            case CAMPAY       -> campayService;
            case MTN          -> mtnMomoService;
            case ORANGE_MONEY -> orangeMoneyService;
            case EQLINK       -> eqLinkPaymentService;
        };
    }
}
