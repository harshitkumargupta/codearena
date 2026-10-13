package com.codearena.api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client address used for per-IP limits. By default it is the connection's remote address and
 * {@code X-Forwarded-For} is ignored, because any client can send that header. Only when the connection comes from a
 * configured trusted proxy is the header read, right to left, skipping the trusted proxies.
 */
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final Set<String> trustedProxies;

    public ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = Set.copyOf(trustedProxies);
        if (this.trustedProxies.isEmpty()) {
            log.info("Rate limits use the connection's remote address; X-Forwarded-For is not trusted "
                    + "(set RATE_LIMIT_TRUSTED_PROXIES to trust a reverse proxy)");
        } else {
            log.info("Rate limits trust X-Forwarded-For only from: {}", this.trustedProxies);
        }
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) {
            return remote;
        }
        String header = request.getHeader(FORWARDED_FOR);
        if (header == null || header.isBlank()) {
            return remote;
        }
        String[] hops = header.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remote;
    }
}
