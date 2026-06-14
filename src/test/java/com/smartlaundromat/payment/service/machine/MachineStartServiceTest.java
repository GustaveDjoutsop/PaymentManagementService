package com.smartlaundromat.payment.service.machine;

import com.smartlaundromat.payment.eqlink.EqLinkProperties;
import com.smartlaundromat.payment.model.Transaction;
import com.smartlaundromat.payment.model.enums.PaymentProvider;
import com.smartlaundromat.payment.model.enums.PaymentStatus;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineStartServiceTest {

    @Mock
    EqLinkProperties eqLinkProperties;

    @Mock
    RestTemplate restTemplate;

    MachineStartService machineStartService;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        machineStartService = new MachineStartService(eqLinkProperties, restTemplate,
                CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
        ReflectionTestUtils.setField(machineStartService, "machineStateServiceUrl", "http://localhost:8082");

        transaction = Transaction.builder()
                .externalReference("EXT-001")
                .machineId("MACH-01")
                .amount(new BigDecimal("1000"))
                .pulseCount(2)
                .cycleDuration(30)
                .status(PaymentStatus.SUCCESSFUL)
                .paymentProvider(PaymentProvider.CAMPAY)
                .rfidCardUid("ABC123")
                .build();
    }

    @Test
    void shouldNotifyMachineStartWhenAutoStartIsEnabled() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(true);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        // when
        machineStartService.notifyMachineStart(transaction);

        // then
        verify(restTemplate).postForEntity(
                eq("http://localhost:8082/api/machines/start-cycle"),
                any(HttpEntity.class),
                eq(Map.class));
    }

    @Test
    void shouldSkipWhenAutoStartIsDisabled() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(false);

        // when
        machineStartService.notifyMachineStart(transaction);

        // then
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldSkipWhenMachineIdIsNull() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(true);
        transaction.setMachineId(null);

        // when
        machineStartService.notifyMachineStart(transaction);

        // then
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldSkipWhenMachineIdIsEmpty() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(true);
        transaction.setMachineId("");

        // when
        machineStartService.notifyMachineStart(transaction);

        // then
        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldUseDefaultValuesWhenCycleDurationAndPulseCountAreNull() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(true);
        transaction.setPulseCount(null);
        transaction.setCycleDuration(null);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        // when
        machineStartService.notifyMachineStart(transaction);

        // then
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void shouldNotIncludeRfidCardUidWhenNull() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(true);
        transaction.setRfidCardUid(null);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        // when
        machineStartService.notifyMachineStart(transaction);

        // then
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void shouldSwallowExceptionOnRestTemplateFailure() {
        // given
        when(eqLinkProperties.isAutoStartMachineAfterPayment()).thenReturn(true);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // when — should not throw
        machineStartService.notifyMachineStart(transaction);

        // then
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }
}
