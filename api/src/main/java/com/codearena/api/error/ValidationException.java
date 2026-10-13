package com.codearena.api.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    public ValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, "Validation failed", List.of(new ApiError.FieldError(field, message)));
    }
}
