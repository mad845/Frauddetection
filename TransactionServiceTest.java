package com.frauddetect.service;

import com.frauddetect.dto.FraudAlertDto;
import com.frauddetect.dto.TransactionRequest;
import com.frauddetect.model.Account;
import com.frauddetect.model.Transaction;
import com.frauddetect.repository.AccountRepository;
import com.frauddetect.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private KafkaProducerService kafkaProducerService;
    @Mock private AlertBroadcastService alertBroadcastService;

    private TransactionService transactionService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, accountRepository, kafkaProducerService, alertBroadcastService);

        testAccount = new Account();
        testAccount.setId(1L);
        testAccount.setAccountNumber("ACC-0001");
        testAccount.setOwnerName("Ana Torres");
        testAccount.setBalance(new BigDecimal("5000.00"));
        testAccount.setCountry("AR");
    }

    @Test
    void createTransaction_deberiaGuardarYPublicarEnKafka() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountNumber("ACC-0001");
        request.setAmount(new BigDecimal("150.00"));
        request.setMerchant("Tienda Online");
        request.setMerchantCountry("AR");
        request.setChannel("ONLINE");

        when(accountRepository.findByAccountNumber("ACC-0001")).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(100L);
            return tx;
        });
        when(transactionRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        Transaction result = transactionService.createTransaction(request);

        assertNotNull(result.getTransactionUuid());
        assertEquals("PENDING", result.getStatus());
        assertEquals(new BigDecimal("150.00"), result.getAmount());

        // Verificamos que se publico el payload correcto a Kafka
        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaProducerService, times(1)).publishTransaction(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("ACC-0001", payload.get("accountNumber"));
        assertEquals(false, payload.get("isForeignCountry"));
    }

    @Test
    void createTransaction_deberiaMarcarPaisExtranjeroCorrectamente() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountNumber("ACC-0001");
        request.setAmount(new BigDecimal("500.00"));
        request.setMerchant("Tienda Global");
        request.setMerchantCountry("US"); // distinto del pais de la cuenta (AR)
        request.setChannel("ONLINE");

        when(accountRepository.findByAccountNumber("ACC-0001")).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        transactionService.createTransaction(request);

        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaProducerService).publishTransaction(payloadCaptor.capture());
        assertEquals(true, payloadCaptor.getValue().get("isForeignCountry"));
    }

    @Test
    void createTransaction_deberiaLanzarExcepcionSiLaCuentaNoExiste() {
        TransactionRequest request = new TransactionRequest();
        request.setAccountNumber("ACC-9999");
        request.setAmount(new BigDecimal("100.00"));

        when(accountRepository.findByAccountNumber("ACC-9999")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(request));
        verify(kafkaProducerService, never()).publishTransaction(any());
    }

    @Test
    void applyFraudVerdict_deberiaActualizarEstadoYNotificar() {
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setTransactionUuid("uuid-123");
        tx.setAccount(testAccount);
        tx.setAmount(new BigDecimal("9000.00"));
        tx.setStatus("PENDING");

        FraudAlertDto alert = new FraudAlertDto();
        alert.setTransactionUuid("uuid-123");
        alert.setRiskScore(0.92);
        alert.setVerdict("REJECTED");
        alert.setReason("Patron altamente anomalo");

        when(transactionRepository.findByTransactionUuid("uuid-123")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        transactionService.applyFraudVerdict(alert);

        assertEquals("REJECTED", tx.getStatus());
        assertEquals(0.92, tx.getRiskScore());
        verify(alertBroadcastService, times(1)).broadcastAlert(eq(tx), eq("Patron altamente anomalo"));
    }

    @Test
    void applyFraudVerdict_deberiaLanzarExcepcionSiLaTransaccionNoExiste() {
        FraudAlertDto alert = new FraudAlertDto();
        alert.setTransactionUuid("no-existe");

        when(transactionRepository.findByTransactionUuid("no-existe")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> transactionService.applyFraudVerdict(alert));
        verify(alertBroadcastService, never()).broadcastAlert(any(), any());
    }

    @Test
    void getFlaggedTransactions_deberiaDelegarAlRepositorio() {
        List<Transaction> flagged = List.of(new Transaction());
        when(transactionRepository.findByStatusOrderByCreatedAtDesc("FLAGGED")).thenReturn(flagged);

        List<Transaction> result = transactionService.getFlaggedTransactions();

        assertEquals(1, result.size());
        verify(transactionRepository).findByStatusOrderByCreatedAtDesc("FLAGGED");
    }
}
