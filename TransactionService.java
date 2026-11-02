package com.frauddetect.service;

import com.frauddetect.dto.FraudAlertDto;
import com.frauddetect.dto.TransactionRequest;
import com.frauddetect.model.Account;
import com.frauddetect.model.Transaction;
import com.frauddetect.repository.AccountRepository;
import com.frauddetect.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final KafkaProducerService kafkaProducerService;
    private final AlertBroadcastService alertBroadcastService;

    public TransactionService(TransactionRepository transactionRepository,
                               AccountRepository accountRepository,
                               KafkaProducerService kafkaProducerService,
                               AlertBroadcastService alertBroadcastService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.alertBroadcastService = alertBroadcastService;
    }

    public Transaction createTransaction(TransactionRequest request) {
        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada: " + request.getAccountNumber()));

        Transaction tx = new Transaction();
        tx.setTransactionUuid(UUID.randomUUID().toString());
        tx.setAccount(account);
        tx.setAmount(request.getAmount());
        tx.setMerchant(request.getMerchant());
        tx.setMerchantCountry(request.getMerchantCountry());
        tx.setChannel(request.getChannel());
        tx.setStatus("PENDING");

        Transaction saved = transactionRepository.save(tx);

        // Calculamos algunas features simples que ayudan al modelo de Python
        List<Transaction> recentTx = transactionRepository.findTop50ByOrderByCreatedAtDesc();
        long txLastHourSameAccount = recentTx.stream()
                .filter(t -> t.getAccount().getId().equals(account.getId()))
                .filter(t -> t.getCreatedAt().isAfter(LocalDateTime.now().minus(1, ChronoUnit.HOURS)))
                .count();

        boolean isForeignCountry = request.getMerchantCountry() != null
                && !request.getMerchantCountry().equalsIgnoreCase(account.getCountry());

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionUuid", saved.getTransactionUuid());
        payload.put("accountId", account.getId());
        payload.put("accountNumber", account.getAccountNumber());
        payload.put("amount", saved.getAmount());
        payload.put("balance", account.getBalance());
        payload.put("merchant", saved.getMerchant());
        payload.put("merchantCountry", saved.getMerchantCountry());
        payload.put("accountCountry", account.getCountry());
        payload.put("channel", saved.getChannel());
        payload.put("txLastHourSameAccount", txLastHourSameAccount);
        payload.put("isForeignCountry", isForeignCountry);
        payload.put("createdAt", saved.getCreatedAt().toString());

        kafkaProducerService.publishTransaction(payload);

        return saved;
    }

    public void applyFraudVerdict(FraudAlertDto alert) {
        Transaction tx = transactionRepository.findByTransactionUuid(alert.getTransactionUuid())
                .orElseThrow(() -> new IllegalArgumentException("Transaccion no encontrada: " + alert.getTransactionUuid()));

        tx.setRiskScore(alert.getRiskScore());
        tx.setStatus(alert.getVerdict());
        transactionRepository.save(tx);

        // Notificamos en tiempo real al dashboard via WebSocket
        alertBroadcastService.broadcastAlert(tx, alert.getReason());
    }

    public List<Transaction> getRecentTransactions() {
        return transactionRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public List<Transaction> getFlaggedTransactions() {
        return transactionRepository.findByStatusOrderByCreatedAtDesc("FLAGGED");
    }
}
