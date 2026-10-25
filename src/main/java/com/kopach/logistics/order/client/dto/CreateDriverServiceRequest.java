package com.kopach.logistics.order.client.dto;

public class CreateDriverServiceRequest {

    private Long userId;
    private String name;
    private String phone;

    public CreateDriverServiceRequest() {
    }

    public CreateDriverServiceRequest(Long userId, String name, String phone) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}