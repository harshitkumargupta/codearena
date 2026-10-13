package com.codearena.api.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(HttpStatus status, String message) {
        this(status, message, List.of());
    }

    public ApiException(HttpStatus status, String message, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
