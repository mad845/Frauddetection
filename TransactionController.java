package com.frauddetect.controller;

import com.frauddetect.dto.TransactionRequest;
import com.frauddetect.model.Transaction;
import com.frauddetect.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
        Transaction tx = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Transaction>> getRecent() {
        return ResponseEntity.ok(transactionService.getRecentTransactions());
    }

    @GetMapping("/flagged")
    public ResponseEntity<List<Transaction>> getFlagged() {
        return ResponseEntity.ok(transactionService.getFlaggedTransactions());
    }
}
