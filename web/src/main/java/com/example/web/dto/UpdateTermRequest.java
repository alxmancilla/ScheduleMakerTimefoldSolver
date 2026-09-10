package com.example.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for PUT /api/admin/term: set the current term/period label. */
public class UpdateTermRequest {

    @NotNull(message = "Label is required (use an empty string to clear it)")
    @Size(max = 100, message = "Label must not exceed 100 characters")
    private String label;

    public UpdateTermRequest() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
