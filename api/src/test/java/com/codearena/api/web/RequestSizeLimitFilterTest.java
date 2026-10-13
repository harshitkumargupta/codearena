package com.codearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.error.ErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new HttpLimitsProperties(2048, 4096),
            new ErrorResponseWriter(JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC()));

    @Test
    void declaredLengthOverTheLimitIsRejectedWith413BeforeReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/submissions");
        request.setContent(new byte[3000]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("chain must not run");
        });

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("message").asText())
                .isEqualTo("Request body is too large (limit 2 KiB)");
    }

    @Test
    void testCaseUploadHasTheLargerLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/problems/3/testcases");
        request.setContent(new byte[3000]);
        boolean[] ran = {false};

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> ran[0] = true);

        assertThat(ran[0]).isTrue();
    }

    @Test
    void chunkedBodyIsCountedWhileReading() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/submissions") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[5000]);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes()))
                .isInstanceOf(PayloadTooLargeException.class);
    }
}
