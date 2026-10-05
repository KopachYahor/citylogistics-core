package com.kopach.logistics.order.controller;

import com.kopach.logistics.order.dto.CreateOrderRequest;
import com.kopach.logistics.order.dto.OrderResponse;
import com.kopach.logistics.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestBody @Valid CreateOrderRequest request,
            @RequestParam Long clientId) {
        return ResponseEntity.ok(orderService.createOrder(request, clientId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> getMy(@RequestParam Long clientId) {
        return ResponseEntity.ok(orderService.getMyOrders(clientId));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @RequestParam Long clientId) {
        return ResponseEntity.ok(orderService.cancelOrder(id, clientId));
    }
}