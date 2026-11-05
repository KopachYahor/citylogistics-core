package com.kopach.logistics.common;

/**
 * Запрошенный объект не существует. GlobalExceptionHandler превращает его в 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
