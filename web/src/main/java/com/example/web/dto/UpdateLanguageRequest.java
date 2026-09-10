package com.example.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body for PUT /api/auth/preferred-language: the caller's own new UI language. */
public class UpdateLanguageRequest {

    @NotBlank(message = "Language is required")
    @Pattern(regexp = "^(en|es)$", message = "Language must be 'en' or 'es'")
    private String language;

    public UpdateLanguageRequest() {
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
