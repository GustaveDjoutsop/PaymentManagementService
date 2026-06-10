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
import com.smartlaundromat.payment.service.provider.MtnMomoService;
import com.smartlaundromat.payment.service.provider.OrangeMoneyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    CampayService campayService;

    @Mock
    MtnMomoService mtnMomoService;

    @Mock
    OrangeMoneyService orangeMoneyService;

    @Mock
    MachineStartService machineStartService;

    @InjectMocks
    PaymentService paymentService;

    private PaymentInitiationRequest request;

    @BeforeEach
    void setUp() {
        request = new PaymentInitiationRequest();
        request.setPhoneNumber("237612345678");
        request.setAmount(new BigDecimal("1000"));
        request.setMachineId("MACH-01");
        request.setPulseCount(2);
        request.setCycleDuration(30);
        request.setProvider(PaymentProvider.CAMPAY);
        request.setDescription("Wash cycle");
    }

    // ── initiatePayment ──────────────────────────────────────────────────────

    @Nested
    class InitiatePayment {

        @Test
        void shouldInitiatePaymentWhenMachineIsFree() {
            // given
            when(transactionRepository.findByMachineIdAndStatus("MACH-01", PaymentStatus.SUCCESSFUL))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.findByMachineIdAndStatus("MACH-01", PaymentStatus.PENDING))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse providerResponse = PaymentResponse.builder()
                    .success(true)
                    .providerReference("CAMP-REF-001")
                    .build();
            when(campayService.requestPayment(anyString(), any(), anyString(), anyString()))
                    .thenReturn(providerResponse);

            // when
            PaymentResponse result = paymentService.initiatePayment(request);

            // then
            assertThat(result.getProviderReference()).isEqualTo("CAMP-REF-001");
            verify(transactionRepository, times(2)).save(any(Transaction.class));
        }

        @Test
        void shouldThrowWhenMachineHasActiveCycle() {
            // given
            Transaction active = Transaction.builder()
                    .machineId("MACH-01")
                    .status(PaymentStatus.SUCCESSFUL)
                    .build();
            when(transactionRepository.findByMachineIdAndStatus("MACH-01", PaymentStatus.SUCCESSFUL))
                    .thenReturn(List.of(active));

            // when / then
            assertThatThrownBy(() -> paymentService.initiatePayment(request))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("active cycle");
        }

        @Test
        void shouldThrowWhenMachineHasPendingPayment() {
            // given
            when(transactionRepository.findByMachineIdAndStatus("MACH-01", PaymentStatus.SUCCESSFUL))
                    .thenReturn(Collections.emptyList());
            Transaction pending = Transaction.builder()
                    .machineId("MACH-01")
                    .status(PaymentStatus.PENDING)
                    .build();
            when(transactionRepository.findByMachineIdAndStatus("MACH-01", PaymentStatus.PENDING))
                    .thenReturn(List.of(pending));

            // when / then
            assertThatThrownBy(() -> paymentService.initiatePayment(request))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("pending payment");
        }

        @Test
        void shouldUseMtnProviderWhenRequested() {
            // given
            request.setProvider(PaymentProvider.MTN);

            when(transactionRepository.findByMachineIdAndStatus(anyString(), eq(PaymentStatus.SUCCESSFUL)))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.findByMachineIdAndStatus(anyString(), eq(PaymentStatus.PENDING)))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse providerResponse = PaymentResponse.builder()
                    .success(true)
                    .providerReference("MTN-REF-001")
                    .build();
            when(mtnMomoService.requestPayment(anyString(), any(), anyString(), anyString()))
                    .thenReturn(providerResponse);

            // when
            PaymentResponse result = paymentService.initiatePayment(request);

            // then
            assertThat(result.getProviderReference()).isEqualTo("MTN-REF-001");
            verify(mtnMomoService).requestPayment(anyString(), any(), anyString(), anyString());
        }

        @Test
        void shouldUseOrangeProviderWhenRequested() {
            // given
            request.setProvider(PaymentProvider.ORANGE_MONEY);

            when(transactionRepository.findByMachineIdAndStatus(anyString(), eq(PaymentStatus.SUCCESSFUL)))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.findByMachineIdAndStatus(anyString(), eq(PaymentStatus.PENDING)))
                    .thenReturn(Collections.emptyList());
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse providerResponse = PaymentResponse.builder()
                    .success(true)
                    .providerReference("ORANGE-REF-001")
                    .build();
            when(orangeMoneyService.requestPayment(anyString(), any(), anyString(), anyString()))
                    .thenReturn(providerResponse);

            // when
            PaymentResponse result = paymentService.initiatePayment(request);

            // then
            verify(orangeMoneyService).requestPayment(anyString(), any(), anyString(), anyString());
        }
    }

    // ── processWebhook ───────────────────────────────────────────────────────

    @Nested
    class ProcessWebhook {

        @Test
        void shouldMarkTransactionSuccessfulWhenStatusIsSuccessful() {
            // given
            Transaction transaction = Transaction.builder()
                    .externalReference("EXT-001")
                    .machineId("MACH-01")
                    .status(PaymentStatus.PENDING)
                    .build();
            when(transactionRepository.findByExternalReference("EXT-001"))
                    .thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            Transaction result = paymentService.processWebhook(
                    PaymentProvider.CAMPAY, "EXT-001", "SUCCESSFUL", "PROV-001", null);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
            assertThat(result.getProviderReference()).isEqualTo("PROV-001");
            verify(machineStartService).notifyMachineStart(transaction);
        }

        @Test
        void shouldMarkTransactionFailedWhenStatusIsNotSuccessful() {
            // given
            Transaction transaction = Transaction.builder()
                    .externalReference("EXT-001")
                    .status(PaymentStatus.PENDING)
                    .build();
            when(transactionRepository.findByExternalReference("EXT-001"))
                    .thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            Transaction result = paymentService.processWebhook(
                    PaymentProvider.CAMPAY, "EXT-001", "FAILED", null, "Insufficient funds");

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getFailureReason()).isEqualTo("Insufficient funds");
            verify(machineStartService, never()).notifyMachineStart(any());
        }

        @Test
        void shouldSkipAlreadySuccessfulTransaction() {
            // given
            Transaction transaction = Transaction.builder()
                    .externalReference("EXT-001")
                    .status(PaymentStatus.SUCCESSFUL)
                    .build();
            when(transactionRepository.findByExternalReference("EXT-001"))
                    .thenReturn(Optional.of(transaction));

            // when
            Transaction result = paymentService.processWebhook(
                    PaymentProvider.CAMPAY, "EXT-001", "SUCCESSFUL", "PROV-001", null);

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
            verify(transactionRepository, never()).save(any());
            verify(machineStartService, never()).notifyMachineStart(any());
        }

        @Test
        void shouldThrowWhenTransactionNotFound() {
            // given
            when(transactionRepository.findByExternalReference("INVALID"))
                    .thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> paymentService.processWebhook(
                    PaymentProvider.CAMPAY, "INVALID", "SUCCESSFUL", null, null))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Transaction not found");
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Test
    void shouldGetTransactionByReference() {
        // given
        Transaction tx = Transaction.builder().externalReference("EXT-001").build();
        when(transactionRepository.findByExternalReference("EXT-001")).thenReturn(Optional.of(tx));

        // when
        Transaction result = paymentService.getTransactionByReference("EXT-001");

        // then
        assertThat(result.getExternalReference()).isEqualTo("EXT-001");
    }

    @Test
    void shouldThrowWhenTransactionByReferenceNotFound() {
        // given
        when(transactionRepository.findByExternalReference("INVALID")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> paymentService.getTransactionByReference("INVALID"))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void shouldGetTransactionsByMachine() {
        // given
        when(transactionRepository.findByMachineIdOrderByCreatedAtDesc("MACH-01"))
                .thenReturn(List.of(Transaction.builder().machineId("MACH-01").build()));

        // when
        List<Transaction> result = paymentService.getTransactionsByMachine("MACH-01");

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetTransactionsByCard() {
        // given
        when(transactionRepository.findByRfidCardUidOrderByCreatedAtDesc("ABC123"))
                .thenReturn(List.of(Transaction.builder().rfidCardUid("ABC123").build()));

        // when
        List<Transaction> result = paymentService.getTransactionsByCard("ABC123");

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetProviderStatus() {
        // given
        when(campayService.isConfigured()).thenReturn(true);
        when(mtnMomoService.isConfigured()).thenReturn(false);
        when(orangeMoneyService.isConfigured()).thenReturn(true);

        // when
        Map<String, Object> result = paymentService.getProviderStatus();

        // then
        assertThat(result).containsKeys("campay", "mtn", "orange_money");
    }
}
