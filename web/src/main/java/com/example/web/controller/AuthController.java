package com.example.web.controller;

import com.example.web.dto.LoginRequest;
import com.example.web.dto.LoginResponse;
import com.example.web.dto.UserInfoResponse;
import com.example.web.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints. Login exchanges username/password for a signed JWT;
 * /me echoes the current authenticated user and role (from the validated token).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String role = extractRole(authentication);
        String token = tokenService.issueToken(authentication.getName(), role);
        return new LoginResponse(token, authentication.getName(), role, tokenService.getTtlSeconds());
    }

    @GetMapping("/me")
    public UserInfoResponse me(Authentication authentication) {
        return new UserInfoResponse(authentication.getName(), extractRole(authentication));
    }

    /** The single granted authority is "ROLE_<role>"; expose the bare role. */
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }
}
