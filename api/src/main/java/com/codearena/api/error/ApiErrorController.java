package com.codearena.api.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's /error endpoint so errors raised outside Spring MVC (filters, the container) use the same body as
 * every other error. Exception details are never included.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private final ErrorResponseWriter writer;

    public ApiErrorController(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @RequestMapping("${server.error.path:/error}")
    public void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value && HttpStatus.resolve(value) != null
                ? HttpStatus.valueOf(value) : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = switch (status) {
            case NOT_FOUND -> "Resource not found";
            case BAD_REQUEST -> "Bad request";
            case PAYLOAD_TOO_LARGE -> "Request body is too large";
            default -> status.is5xxServerError() ? "Unexpected error" : status.getReasonPhrase();
        };
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        writer.write(request, response, status, message, uri instanceof String path ? path : request.getRequestURI());
    }
}
