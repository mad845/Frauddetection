package com.frauddetect.dto;

public class FraudAlertDto {

    private String transactionUuid;
    private double riskScore;
    private String verdict; // APPROVED, FLAGGED, REJECTED
    private String reason;

    public String getTransactionUuid() { return transactionUuid; }
    public void setTransactionUuid(String transactionUuid) { this.transactionUuid = transactionUuid; }

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
