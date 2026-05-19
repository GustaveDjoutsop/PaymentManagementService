package com.smartlaundromat.payment.controller;

import com.smartlaundromat.payment.dto.PaymentInitiationRequest;
import com.smartlaundromat.payment.dto.PaymentResponse;
import com.smartlaundromat.payment.model.Transaction;
import com.smartlaundromat.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentInitiationRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transaction/{reference}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String reference) {
        return ResponseEntity.ok(paymentService.getTransactionByReference(reference));
    }

    @GetMapping("/machine/{machineId}")
    public ResponseEntity<List<Transaction>> getTransactionsByMachine(@PathVariable String machineId) {
        return ResponseEntity.ok(paymentService.getTransactionsByMachine(machineId));
    }

    @GetMapping("/card/{cardUid}")
    public ResponseEntity<List<Transaction>> getTransactionsByCard(@PathVariable String cardUid) {
        return ResponseEntity.ok(paymentService.getTransactionsByCard(cardUid));
    }

    @GetMapping("/providers/status")
    public ResponseEntity<Map<String, Object>> getProviderStatus() {
        return ResponseEntity.ok(paymentService.getProviderStatus());
    }
}
