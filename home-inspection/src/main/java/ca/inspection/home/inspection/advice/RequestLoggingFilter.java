package ca.inspection.home.inspection.advice;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(REQUEST_ID, requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = System.currentTimeMillis() - start;
            if (shouldLog(req)) {
                int status = res.getStatus();
                if (status >= 500) {
                    log.error("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), status, ms);
                } else if (status >= 400) {
                    log.warn("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), status, ms);
                } else {
                    log.info("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), status, ms);
                }
            }
            MDC.remove(REQUEST_ID);
        }
    }

    private static boolean shouldLog(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri == null) return true;
        // Silence static assets — they'd drown out the interesting lines.
        return !(uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/html/")
                || uri.endsWith(".css") || uri.endsWith(".js") || uri.endsWith(".ico")
                || uri.endsWith(".png") || uri.endsWith(".jpg") || uri.endsWith(".jpeg")
                || uri.endsWith(".svg") || uri.endsWith(".woff") || uri.endsWith(".woff2"));
    }
}
