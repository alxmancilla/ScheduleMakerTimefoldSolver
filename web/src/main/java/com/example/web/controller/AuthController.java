package com.example.web.controller;

import com.example.web.dto.LoginRequest;
import com.example.web.dto.LoginResponse;
import com.example.web.dto.UpdateLanguageRequest;
import com.example.web.dto.UserInfoResponse;
import com.example.web.entity.AppUserEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.AppUserRepository;
import com.example.web.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints. Login exchanges username/password for a signed JWT;
 * /me echoes the current authenticated user, role, and preferred UI language
 * (from the validated token plus a lookup for the language, which isn't a JWT
 * claim); /preferred-language lets a user update their own language choice.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AppUserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
            AppUserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String role = extractRole(authentication);
        String token = tokenService.issueToken(authentication.getName(), role);
        String preferredLanguage = userRepository.findById(authentication.getName())
                .map(AppUserEntity::getPreferredLanguage)
                .orElse("en");
        return new LoginResponse(token, authentication.getName(), role, tokenService.getTtlSeconds(), preferredLanguage);
    }

    @GetMapping("/me")
    public UserInfoResponse me(Authentication authentication) {
        String preferredLanguage = userRepository.findById(authentication.getName())
                .map(AppUserEntity::getPreferredLanguage)
                .orElse("en");
        return new UserInfoResponse(authentication.getName(), extractRole(authentication), preferredLanguage);
    }

    @PutMapping("/preferred-language")
    public UserInfoResponse updatePreferredLanguage(Authentication authentication,
            @Valid @RequestBody UpdateLanguageRequest request) {
        AppUserEntity user = userRepository.findById(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", authentication.getName()));
        user.setPreferredLanguage(request.getLanguage());
        userRepository.save(user);
        return new UserInfoResponse(user.getUsername(), extractRole(authentication), user.getPreferredLanguage());
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
