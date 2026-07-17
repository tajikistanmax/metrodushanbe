package tj.metro.dushanbe.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveIp(request);
        if (!buckets.containsKey(ip) && buckets.size() >= properties.getMaxBuckets()) {
            // Не позволяем атакующему раздувать heap бесконечным числом поддельных IP.
            ip = "rate-limit-overflow";
        }
        TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(properties.getCapacity()));

        if (bucket.tryConsume(properties.getCapacity(), properties.getRefillPerMinute())) {
            filterChain.doFilter(request, response);
        } else {
            // refill-per-minute конфигурируем; при 0 (пополнения нет) не делим на ноль.
            int refillPerMinute = properties.getRefillPerMinute();
            long retryAfterSeconds = refillPerMinute > 0 ? 60L / refillPerMinute : 60L;
            if (retryAfterSeconds < 1) {
                retryAfterSeconds = 1;
            }
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"Too many requests, try again later\"}");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/v1/") || path.startsWith("/actuator/health");
    }

    private String resolveIp(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private static class TokenBucket {
        private double tokens;
        private Instant lastRefillTime;

        TokenBucket(double capacity) {
            this.tokens = capacity;
            this.lastRefillTime = Instant.now();
        }

        synchronized boolean tryConsume(double capacity, double refillPerMinute) {
            Instant now = Instant.now();
            double elapsedSeconds = Duration.between(lastRefillTime, now).toMillis() / 1000.0;
            double refill = elapsedSeconds * refillPerMinute / 60.0;
            tokens = Math.min(capacity, tokens + refill);
            lastRefillTime = now;

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
