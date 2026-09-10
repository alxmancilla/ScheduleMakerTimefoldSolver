package com.example.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for PUT /api/groups/{groupId}/courses/{courseName}/default-teacher.
 * teacherId is nullable: a null value clears the pre-assignment.
 */
public class SetGroupCourseTeacherRequest {

    @Size(max = 100, message = "Teacher ID must not exceed 100 characters")
    private String teacherId;

    public SetGroupCourseTeacherRequest() {
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }
}
