package com.kopach.logistics.common;

/**
 * Пользователь известен, но действие ему запрещено (например, чужой заказ).
 * GlobalExceptionHandler превращает его в 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
