package com.codearena.api.security;

import com.codearena.api.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
