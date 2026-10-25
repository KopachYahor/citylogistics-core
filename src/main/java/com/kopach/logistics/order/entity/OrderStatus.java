package com.kopach.logistics.order.entity;

public enum OrderStatus {
    NEW,
    ACCEPTED,
    IN_TRANSIT,
    ARRIVED,
    DELIVERED,
    CANCELLED;


    public OrderStatus next() {
        return switch (this) {
            case NEW -> ACCEPTED;
            case ACCEPTED -> IN_TRANSIT;
            case IN_TRANSIT -> ARRIVED;
            case ARRIVED -> DELIVERED;
            case DELIVERED, CANCELLED -> null;
        };
    }


    public boolean canTransitionTo(OrderStatus target) {
        return this.next() == target;
    }
}