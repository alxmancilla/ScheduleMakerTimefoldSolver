package com.example.web.dto;

import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for StudentGroup with validation. The group's ID is only
 * required/validated on creation; on update it comes from the path.
 */
public class StudentGroupDTO {

    /**
     * Validation group applied only on creation, where the ID is supplied by the
     * client. On update the ID comes from the path and is ignored in the payload.
     */
    public interface Create {
    }

    @NotBlank(message = "Group ID is required", groups = Create.class)
    @Size(max = 100, message = "Group ID must not exceed 100 characters", groups = Create.class)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Group ID must contain only letters, numbers, hyphens, and underscores", groups = Create.class)
    private String id;

    @NotBlank(message = "Group name is required")
    @Size(min = 2, max = 200, message = "Group name must be between 2 and 200 characters")
    private String name;

    /** Optional headcount; when set alongside a room's capacity, a soft
     * constraint warns if this group is placed in a room too small for it. */
    @Min(value = 1, message = "Student count must be at least 1")
    private Integer studentCount;

    public StudentGroupDTO() {
    }

    public StudentGroupDTO(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(Integer studentCount) {
        this.studentCount = studentCount;
    }
}
