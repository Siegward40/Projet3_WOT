package wot.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bearer token on /things/** and /events/** (token: gateway.token). Dashboard files are public.
 * TODO (bonus): roles viewer / operator, 403.
 */
@Component
public class TokenFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final String expectedToken;
    private final String viewerToken;

    public TokenFilter(@Value("${gateway.token}") String expectedToken,
                       @Value("${gateway.viewer-token:}") String viewerToken) {
        this.expectedToken = expectedToken;
        this.viewerToken = viewerToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/things") || path.startsWith("/events"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length()).trim();
        } else if (request.getRequestURI().equals("/events/stream")) {
            // EventSource cannot send headers
            token = request.getParameter("token");
        }

        
        if(matches(token, expectedToken)) {
            chain.doFilter(request, response);
        } else if (matches(token, viewerToken)) {
            if(READ_METHODS.contains(request.getMethod())) {
                chain.doFilter(request, response);
            } else {
                reject(response, HttpServletResponse.SC_FORBIDDEN, "the viewer role is read-only");
            }
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "missing or invalid token");
        }
    }

    private static boolean matches(String given, String expected) {
        return given != null && !expected.isEmpty()
                && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + message + "\"}");
    }
}
