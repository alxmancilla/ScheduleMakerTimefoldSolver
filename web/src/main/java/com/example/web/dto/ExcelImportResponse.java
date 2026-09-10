package com.example.web.dto;

import com.example.web.service.ExcelImportService;

import java.util.List;

/**
 * Result of an admin-triggered Excel import: either a list of validation
 * errors (nothing was written to the database) or per-sheet counts of rows
 * upserted.
 */
public class ExcelImportResponse {

    private final boolean success;
    private final List<String> errors;
    private final int teachersImported;
    private final int coursesImported;
    private final int roomsImported;
    private final int groupsImported;
    private final int groupCoursesImported;

    public ExcelImportResponse(ExcelImportService.ImportResult result) {
        this.success = result.isSuccess();
        this.errors = result.getErrors();
        this.teachersImported = result.getTeachersImported();
        this.coursesImported = result.getCoursesImported();
        this.roomsImported = result.getRoomsImported();
        this.groupsImported = result.getGroupsImported();
        this.groupCoursesImported = result.getGroupCoursesImported();
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getErrors() {
        return errors;
    }

    public int getTeachersImported() {
        return teachersImported;
    }

    public int getCoursesImported() {
        return coursesImported;
    }

    public int getRoomsImported() {
        return roomsImported;
    }

    public int getGroupsImported() {
        return groupsImported;
    }

    public int getGroupCoursesImported() {
        return groupCoursesImported;
    }
}
