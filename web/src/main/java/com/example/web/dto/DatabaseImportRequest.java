package com.example.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Which existing database/backups/*.sql file to restore from. */
public class DatabaseImportRequest {

    @NotBlank
    private String filename;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
