package com.codearena.api.support;

import com.codearena.api.config.ClockConfig;
import com.codearena.api.error.ErrorResponseWriter;
import com.codearena.api.security.JwtService;
import com.codearena.api.security.RestAccessDeniedHandler;
import com.codearena.api.security.RestAuthenticationEntryPoint;
import com.codearena.api.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({SecurityConfig.class, JwtService.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ErrorResponseWriter.class, ClockConfig.class})
public class WebSecurityTestConfig {
}
