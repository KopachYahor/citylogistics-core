package com.kopach.logistics.driver.dto;

import java.io.Serializable;

public class DriverAssignedEvent implements Serializable {

    private Long orderId;
    private Long driverId;

    public DriverAssignedEvent() {
    }

    public DriverAssignedEvent(Long orderId, Long driverId) {
        this.orderId = orderId;
        this.driverId = driverId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
}