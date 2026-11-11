package com.example.myaimproject.dto;


public class OrderDTO {
    private String paypalOrderId;
    private String amount;
    private String currency;
    private String status;

    // Constructor
    public OrderDTO(String paypalOrderId, String amount, String currency, String status) {
        this.paypalOrderId = paypalOrderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    // Getters and Setters
    public String getPaypalOrderId() { return paypalOrderId; }
    public void setPaypalOrderId(String paypalOrderId) { this.paypalOrderId = paypalOrderId; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
