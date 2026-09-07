package com.example.web.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Stateless security: authenticate with a signed JWT (HMAC) carrying the user's
 * role. Reads are open to any authenticated role; writes require WRITER,
 * SCHEDULER, or ADMIN; user management under /api/admin/** requires ADMIN
 * specifically, except two carve-outs described below. SCHEDULER (added
 * 2026-09-07) is WRITER-plus: everything WRITER can do, plus triggering/
 * configuring the solver and full control over the schedule itself (see
 * below) - a persona for "runs and tunes the solver, fixes what comes out"
 * without handing them user management, audit logs, or DB backup/restore.
 *
 * Course block assignments (/api/assignments/**) and schedule-view editing
 * are the one resource-specific exception to the general write rule, and -
 * since 2026-09-07 - to the general WRITER-can-write rule too: reads are open
 * the same as everywhere else (READER/WRITER/SCHEDULER/ADMIN), but every
 * write (the move/pin editor's PUT .../move, its live-check POST
 * .../validate-move, and the general POST/PUT/DELETE, including /export
 * /import) requires SCHEDULER or ADMIN specifically - WRITER does not get
 * its usual write access here, unlike every other domain-data resource.
 * (Before 2026-09-07, WRITER had this access via .../move and
 * .../validate-move; it was intentionally removed when SCHEDULER was
 * introduced so schedule editing has exactly one non-ADMIN owner.)
 *
 * Solver triggering/config (/api/admin/engine/**) and constraint weights
 * (/api/admin/constraint-config/**) are carved out of the general ADMIN-only
 * /api/admin/** rule to admit SCHEDULER too - every other /api/admin/**
 * resource (users, audit log, DB backup/restore, admin report snapshots,
 * and the remaining Settings-tab config resources: blocks, calendar
 * exceptions, component block rules, semester hour limits, timeslots) stays
 * ADMIN-only, since none of it is a scheduling concern.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecretKey hmacKey;
    private final String[] allowedOrigins;

    public SecurityConfig(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String[] allowedOrigins) {
        this.hmacKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthConverter)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        // A personal UI preference, not a domain-data write: any authenticated
                        // role (including READER) may update their own, unlike the general PUT
                        // rule below which requires WRITER/SCHEDULER/ADMIN.
                        .requestMatchers(HttpMethod.PUT, "/api/auth/preferred-language")
                        .hasAnyRole("READER", "WRITER", "SCHEDULER", "ADMIN", "TEACHER")
                        // Solver triggering/config and constraint weights are scheduling
                        // concerns, not admin ones - carved out of the general ADMIN-only
                        // /api/admin/** rule below so SCHEDULER reaches them too, ahead of
                        // that blanket rule which still governs every other /api/admin/**
                        // resource (users, audit log, DB backup, etc).
                        .requestMatchers("/api/admin/engine/**").hasAnyRole("SCHEDULER", "ADMIN")
                        .requestMatchers("/api/admin/constraint-config/**").hasAnyRole("SCHEDULER", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Course block assignments carry the live/solved schedule - reads stay
                        // open to READER/WRITER/SCHEDULER/ADMIN like everywhere else, but every
                        // write (POST/PUT/DELETE, including the /export /import endpoints) needs
                        // SCHEDULER or ADMIN specifically, ahead of the general per-method rules
                        // below which would otherwise let plain WRITER write here too.
                        .requestMatchers(HttpMethod.GET, "/api/assignments/**")
                        .hasAnyRole("READER", "WRITER", "SCHEDULER", "ADMIN")
                        // validate-move computes and returns a result, it doesn't write anything,
                        // but it exists only in service of the move/pin editor below - scoped to
                        // the same SCHEDULER/ADMIN audience as the write itself, not the broader
                        // read audience the GET rule above gets.
                        .requestMatchers(HttpMethod.POST, "/api/assignments/*/validate-move")
                        .hasAnyRole("SCHEDULER", "ADMIN")
                        // move only ever sets blockTimeslotId/pinned (never room/teacher/course),
                        // re-validated server-side - narrower in scope than the general PUT rule
                        // right below (which governs the full-DTO edit endpoint, room/teacher/
                        // course included), but the same SCHEDULER/ADMIN audience as it.
                        .requestMatchers(HttpMethod.PUT, "/api/assignments/*/move")
                        .hasAnyRole("SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/assignments/**").hasAnyRole("SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/assignments/**").hasAnyRole("SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/assignments/**").hasAnyRole("SCHEDULER", "ADMIN")
                        // TEACHER is deliberately NOT in the general GET rule below - it can only
                        // read its own schedule/identity/term, not the broader domain data every
                        // other role can. Without this exception, GET /api/auth/me (used on every
                        // page load to hydrate the session) would 403 for TEACHER and immediately
                        // log them back out.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me")
                        .hasAnyRole("TEACHER", "READER", "WRITER", "SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/schedule/view/me")
                        .hasAnyRole("TEACHER", "READER", "WRITER", "SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/term")
                        .hasAnyRole("READER", "WRITER", "SCHEDULER", "ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("READER", "WRITER", "SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("WRITER", "SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("WRITER", "SCHEDULER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("WRITER", "SCHEDULER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(hmacKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Map the single "role" claim to a ROLE_-prefixed authority. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns (not setAllowedOrigins) so a wildcard like
        // https://*.trycloudflare.com can match the random hostname a fresh
        // cloudflared quick tunnel gets on every run. Required (rather than
        // setAllowedOrigins) to combine wildcard matching with allowCredentials.
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
