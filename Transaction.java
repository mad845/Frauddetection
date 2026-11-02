package com.frauddetect.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_uuid", nullable = false, unique = true)
    private String transactionUuid;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private BigDecimal amount;

    private String merchant;

    @Column(name = "merchant_country")
    private String merchantCountry;

    private String channel; // ONLINE, ATM, POS, TRANSFER

    private String status = "PENDING"; // PENDING, APPROVED, FLAGGED, REJECTED

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Transaction() {}

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionUuid() { return transactionUuid; }
    public void setTransactionUuid(String transactionUuid) { this.transactionUuid = transactionUuid; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getMerchantCountry() { return merchantCountry; }
    public void setMerchantCountry(String merchantCountry) { this.merchantCountry = merchantCountry; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
