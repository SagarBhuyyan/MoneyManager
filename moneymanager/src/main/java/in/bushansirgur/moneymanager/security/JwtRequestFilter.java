package in.bushansirgur.moneymanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    // Public endpoints (excluded from JWT check)
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1.0/login",
            "/api/v1.0/register",
            "/api/v1.0/activate",
            "/health",
            "/status",
            "/error"
    );

    @Override
    protected void doFilterInternal(@SuppressWarnings("null") HttpServletRequest request,
                                    @SuppressWarnings("null") HttpServletResponse response,
                                    @SuppressWarnings("null") FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getServletPath();
        String method = request.getMethod();

        log.debug("Incoming {} request to: {}", method, requestPath);

        // JWT Validation Logic
        final String authHeader = request.getHeader("Authorization");
        String email = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                email = jwtUtil.extractUsername(jwt);
                log.debug("Extracted email from token: {}", email);
            } catch (Exception e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                sendUnauthorizedResponse(response, "Invalid or expired token.");
                return;
            }
        } else {
            // No Authorization header - continue without authentication
            log.debug("No Authorization header found for: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {}", email);
                } else {
                    log.warn("JWT token validation failed for user: {}", email);
                    sendUnauthorizedResponse(response, "Invalid or expired token.");
                    return;
                }
            } catch (Exception e) {
                log.error("Error loading user details: {}", e.getMessage());
                sendUnauthorizedResponse(response, "Error authenticating user.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@SuppressWarnings("null") HttpServletRequest request) {
        String path = request.getServletPath();
        boolean shouldNotFilter = EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
        if (shouldNotFilter) {
            log.debug("Skipping JWT filter for: {}", path);
        }
        return shouldNotFilter;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        log.warn("Sending unauthorized response: {}", message);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{"
                + "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\","
                + "\"status\":401,"
                + "\"error\":\"Unauthorized\","
                + "\"message\":\"" + message + "\""
                + "}");
    }
}