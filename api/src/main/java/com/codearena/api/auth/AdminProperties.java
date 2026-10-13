package com.codearena.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("codearena.admin")
public record AdminProperties(String username, String email, String password) {

    public boolean isComplete() {
        return StringUtils.hasText(username) && StringUtils.hasText(email) && StringUtils.hasText(password);
    }
}
