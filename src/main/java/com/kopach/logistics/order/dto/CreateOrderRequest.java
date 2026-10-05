package com.kopach.logistics.order.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class CreateOrderRequest {

    @NotBlank
    private String fromAddress;

    @NotBlank
    private String toAddress;

    private BigDecimal price;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String fromAddress, String toAddress, BigDecimal price) {
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.price = price;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}