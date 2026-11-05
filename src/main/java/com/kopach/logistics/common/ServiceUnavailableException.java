package com.kopach.logistics.common;

/**
 * Другой сервис (например, driver-service) не ответил. Вина не клиента,
 * поэтому GlobalExceptionHandler превращает его в 503.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
