package com.kopach.logistics.order.controller;

import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.order.dto.CreateOrderRequest;
import com.kopach.logistics.order.dto.OrderResponse;
import com.kopach.logistics.order.dto.UpdateOrderStatusRequest;
import com.kopach.logistics.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Orders", description = "Заказы: клиент создаёт и отменяет, назначенный водитель меняет статус")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Создать заказ (CLIENT)",
            description = "Заказ сохраняется в статусе NEW, водителя назначает driver-service через Kafka")
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request, user.getId()));
    }

    @Operation(summary = "Мои заказы (CLIENT)")
    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> getMy(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.getMyOrders(user.getId()));
    }

    @Operation(summary = "Заказы, назначенные мне (DRIVER)")
    @GetMapping("/assigned")
    public ResponseEntity<List<OrderResponse>> getAssigned(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.getAssignedOrders(user.getId()));
    }

    @Operation(summary = "Отменить свой заказ (CLIENT)", description = "Только пока водитель не назначен: статус NEW")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.cancelOrder(id, user.getId()));
    }

    @Operation(summary = "Следующий статус заказа (назначенный DRIVER)",
            description = "Только следующий шаг: ACCEPTED → IN_TRANSIT → ARRIVED → DELIVERED")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateOrderStatusRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.updateStatus(id, request.getStatus(), user.getId()));
    }
}