package com.frauddetect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class TransactionRequest {

    @NotBlank
    private String accountNumber;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String merchant;
    private String merchantCountry;
    private String channel;

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getMerchantCountry() { return merchantCountry; }
    public void setMerchantCountry(String merchantCountry) { this.merchantCountry = merchantCountry; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}
