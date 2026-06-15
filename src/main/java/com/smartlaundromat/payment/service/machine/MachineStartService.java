package com.smartlaundromat.payment.service.machine;

import com.smartlaundromat.payment.eqlink.EqLinkProperties;
import com.smartlaundromat.payment.model.Transaction;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Triggers MachineStateService to start a machine cycle after a successful payment.
 *
 * <p>This service is called by {@link com.smartlaundromat.payment.service.PaymentService}
 * when a transaction transitions to {@code SUCCESSFUL} status, only when
 * {@code eqlink.auto-start-machine-after-payment=true}.
 *
 * <p>MachineStateService then decides internally whether to use EQLink or MQTT
 * to physically start the machine — PaymentManagementService does not need to know.
 *
 * <p>The call is authenticated with an Auth0 M2M Bearer token via the
 * {@code machineStateRestTemplate} bean (see {@code MicroserviceClientConfig}),
 * since MachineStateService requires {@code SCOPE_sls-machine-start} on
 * {@code POST /api/machines/start-cycle}.
 */
@Service
@Slf4j
public class MachineStartService {

    private final EqLinkProperties eqLinkProperties;
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    @Value("${machine-state-service.base-url:http://localhost:8082}")
    private String machineStateServiceUrl;

    public MachineStartService(EqLinkProperties eqLinkProperties,
                                @Qualifier("machineStateRestTemplate") RestTemplate restTemplate,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                BulkheadRegistry bulkheadRegistry) {
        this.eqLinkProperties = eqLinkProperties;
        this.restTemplate = restTemplate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("machineStateService");
        this.bulkhead = bulkheadRegistry.bulkhead("machineStateService");
    }

    /**
     * Asynchronously notifies MachineStateService to start the machine cycle
     * associated with the given transaction.
     *
     * <p>The call is fire-and-forget with exception swallowing — a failure here
     * does not roll back the payment record. The operator must handle it manually
     * or rely on the bot/ESP32 fallback.
     *
     * @param transaction the successfully paid transaction
     */
    public void notifyMachineStart(Transaction transaction) {
        if (eqLinkProperties == null || !eqLinkProperties.isAutoStartMachineAfterPayment()) {
            log.debug("Auto machine start disabled — skipping for tx {}", transaction.getExternalReference());
            return;
        }

        if (!StringUtils.hasText(transaction.getMachineId())) {
            log.warn("Cannot auto-start machine — no machineId in transaction {}",
                    transaction.getExternalReference());
            return;
        }

        try {
            String url = machineStateServiceUrl + "/api/machines/start-cycle";

            Map<String, Object> body = new HashMap<>();
            body.put("machineId",            transaction.getMachineId());
            body.put("cycleType",            "NORMAL");
            body.put("durationMinutes",      transaction.getCycleDuration() != null
                                                ? transaction.getCycleDuration() : 30);
            body.put("pulseCount",           transaction.getPulseCount() != null
                                                ? transaction.getPulseCount() : 1);
            body.put("transactionReference", transaction.getExternalReference());

            if (transaction.getRfidCardUid() != null) {
                body.put("rfidCardUid", transaction.getRfidCardUid());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            Supplier<ResponseEntity<Map>> call = () -> restTemplate.postForEntity(url, entity, Map.class);
            call = Bulkhead.decorateSupplier(bulkhead, call);
            call = CircuitBreaker.decorateSupplier(circuitBreaker, call);
            call.get();

            log.info("Machine start triggered: machine={}, tx={}",
                    transaction.getMachineId(), transaction.getExternalReference());

        } catch (Exception e) {
            log.error("Failed to trigger machine start for tx {}: {}",
                    transaction.getExternalReference(), e.getMessage());
        }
    }
}
