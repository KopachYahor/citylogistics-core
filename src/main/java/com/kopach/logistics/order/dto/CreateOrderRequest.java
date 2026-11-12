package com.kopach.logistics.order.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Лимиты повторяют схему БД: иначе длинное значение дало бы 500 вместо 400
public class CreateOrderRequest {

    @NotBlank
    @Size(max = 500) // from_address VARCHAR(500)
    private String fromAddress;

    @NotBlank
    @Size(max = 500) // to_address VARCHAR(500)
    private String toAddress;

    @NotNull
    @Positive
    @Digits(integer = 8, fraction = 2) // price NUMERIC(10, 2): 8 цифр до запятой, 2 после
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