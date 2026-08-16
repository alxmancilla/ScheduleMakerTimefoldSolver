package com.example.web.security;

import com.example.web.entity.AuditLogEntity;
import com.example.web.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Logs successful (2xx) write requests (POST/PUT/DELETE) to /api/** for
 * basic accountability, without touching any individual controller. Login
 * is excluded (nothing meaningful to attribute the request to before
 * authentication succeeds).
 *
 * The repository is optional (required = false) field injection rather than
 * a constructor dependency: WebMvcConfigurer/HandlerInterceptor beans like
 * this one are auto-detected inside @WebMvcTest slices, which don't load the
 * JPA repository layer - a required constructor dependency here would break
 * the ApplicationContext for every existing @WebMvcTest controller test.
 */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Set<String> LOGGED_METHODS = Set.of("POST", "PUT", "DELETE");

    @Autowired(required = false)
    private AuditLogRepository auditLogRepository;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (auditLogRepository == null) {
            return;
        }
        if (!LOGGED_METHODS.contains(request.getMethod())) {
            return;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.equals("/api/auth/login")) {
            return;
        }
        if (response.getStatus() >= 400) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        auditLogRepository.save(
                new AuditLogEntity(authentication.getName(), request.getMethod(), path, response.getStatus()));
    }
}
