package com.example.web.dto;

import com.example.web.entity.SchoolTermEntity;
import java.time.LocalDateTime;

/** The current term/period label, returned by GET /api/term and PUT /api/admin/term. */
public class SchoolTermResponse {

    private final String label;
    private final LocalDateTime updatedAt;

    public SchoolTermResponse(SchoolTermEntity entity) {
        this.label = entity.getLabel();
        this.updatedAt = entity.getUpdatedAt();
    }

    public String getLabel() {
        return label;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
