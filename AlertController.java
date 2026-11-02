package com.frauddetect.controller;

import com.frauddetect.dto.FraudAlertDto;
import com.frauddetect.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    private final TransactionService transactionService;

    public AlertController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Endpoint que consume el servicio Python para reportar el veredicto de riesgo
    @PostMapping
    public ResponseEntity<Void> receiveAlert(@RequestBody FraudAlertDto alert) {
        transactionService.applyFraudVerdict(alert);
        return ResponseEntity.ok().build();
    }
}
