package com.codearena.api.web;

import com.codearena.api.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Global request body limit, larger for the admin test-case upload. A declared Content-Length over the limit is
 * rejected before anything is read; a body without one (chunked) is counted while it is read.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(HttpLimitsProperties.class)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Pattern TEST_CASE_UPLOAD = Pattern.compile("^/api/admin/problems/\\d+/testcases$");

    private final HttpLimitsProperties limits;
    private final ErrorResponseWriter writer;

    public RequestSizeLimitFilter(HttpLimitsProperties limits, ErrorResponseWriter writer) {
        this.limits = limits;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = TEST_CASE_UPLOAD.matcher(request.getRequestURI()).matches()
                ? limits.maxTestCaseBodyBytes() : limits.maxBodyBytes();
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            writer.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, tooLargeMessage(limit));
            return;
        }
        chain.doFilter(declared >= 0 ? request : new LimitedRequest(request, limit), response);
    }

    public static String tooLargeMessage(long limit) {
        return limit >= 1024 * 1024
                ? "Request body is too large (limit " + limit / (1024 * 1024) + " MiB)"
                : "Request body is too large (limit " + limit / 1024 + " KiB)";
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) throws PayloadTooLargeException {
            count += n;
            if (count > limit) {
                throw new PayloadTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
