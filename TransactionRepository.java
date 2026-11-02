package com.frauddetect.repository;

import com.frauddetect.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionUuid(String uuid);
    List<Transaction> findTop50ByOrderByCreatedAtDesc();
    List<Transaction> findByStatusOrderByCreatedAtDesc(String status);
}
