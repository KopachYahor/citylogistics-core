package com.kopach.logistics.order.dto;

import java.io.Serializable;

public class OrderStatusChangedEvent implements Serializable {

    private Long orderId;
    private Long driverId;
    private String oldStatus;
    private String newStatus;

    public OrderStatusChangedEvent() {
    }

    public OrderStatusChangedEvent(Long orderId, Long driverId, String oldStatus, String newStatus) {
        this.orderId = orderId;
        this.driverId = driverId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
}