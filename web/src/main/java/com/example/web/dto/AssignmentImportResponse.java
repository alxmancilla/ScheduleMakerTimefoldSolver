package com.example.web.dto;

import com.example.web.service.AssignmentExcelService;

import java.util.List;

/**
 * Result of an admin-triggered assignments import: either a list of
 * validation errors (nothing was written to the database) or the count of
 * rows upserted.
 */
public class AssignmentImportResponse {

    private final boolean success;
    private final List<String> errors;
    private final int assignmentsImported;

    public AssignmentImportResponse(AssignmentExcelService.ImportResult result) {
        this.success = result.isSuccess();
        this.errors = result.getErrors();
        this.assignmentsImported = result.getAssignmentsImported();
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getErrors() {
        return errors;
    }

    public int getAssignmentsImported() {
        return assignmentsImported;
    }
}
