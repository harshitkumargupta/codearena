package com.codearena.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void withoutTrustedProxiesForwardedForIsIgnored() {
        assertThat(new ClientIpResolver(List.of()).resolve(request("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    void forwardedForFromAnUntrustedPeerIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5"));

        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxyChainYieldsTheRightmostUntrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5", "10.0.0.6"));

        assertThat(resolver.resolve(request("10.0.0.5", "1.1.1.1, 198.51.100.7, 10.0.0.6"))).isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
    }

    private static MockHttpServletRequest request(String remote, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
