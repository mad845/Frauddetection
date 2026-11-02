package com.frauddetect.service;

import com.frauddetect.model.Transaction;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AlertBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastAlert(Transaction tx, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionUuid", tx.getTransactionUuid());
        payload.put("accountNumber", tx.getAccount().getAccountNumber());
        payload.put("amount", tx.getAmount());
        payload.put("merchant", tx.getMerchant());
        payload.put("status", tx.getStatus());
        payload.put("riskScore", tx.getRiskScore());
        payload.put("reason", reason);
        payload.put("createdAt", tx.getCreatedAt().toString());

        messagingTemplate.convertAndSend("/topic/alerts", payload);
    }
}
