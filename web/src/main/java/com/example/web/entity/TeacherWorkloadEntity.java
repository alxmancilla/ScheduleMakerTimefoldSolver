package com.example.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Read-only mapping of the v_teacher_workload view: each teacher's assigned
 * hours (summed from course_block_assignment.block_length, regardless of
 * solved/pinned status), remaining capacity against max_hours_per_week, and
 * utilization percent - computed server-side so the frontend doesn't need
 * broader access than it actually requires to show a workload column.
 *
 * Backs TeacherController's GET /api/teachers/workload, which - unlike
 * /api/assignments/** - stays under the general GET rule (any authenticated
 * role except TEACHER), matching Teachers.jsx's own access level. Teachers.jsx
 * previously computed this client-side from getAssignments(), which broke for
 * READER/WRITER once /api/assignments/** became ADMIN-only.
 */
@Entity
@Immutable
@Table(name = "v_teacher_workload")
public class TeacherWorkloadEntity {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "last_name", length = 200)
    private String lastName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "max_hours_per_week")
    private Integer maxHoursPerWeek;

    @Column(name = "assigned_hours")
    private Long assignedHours;

    @Column(name = "remaining_capacity")
    private Long remainingCapacity;

    @Column(name = "utilization_percent")
    private BigDecimal utilizationPercent;

    public TeacherWorkloadEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public TeacherWorkloadEntity(String id, String name, String lastName, String fullName, Integer maxHoursPerWeek,
            Long assignedHours, Long remainingCapacity, BigDecimal utilizationPercent) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.fullName = fullName;
        this.maxHoursPerWeek = maxHoursPerWeek;
        this.assignedHours = assignedHours;
        this.remainingCapacity = remainingCapacity;
        this.utilizationPercent = utilizationPercent;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public Integer getMaxHoursPerWeek() {
        return maxHoursPerWeek;
    }

    public Long getAssignedHours() {
        return assignedHours;
    }

    public Long getRemainingCapacity() {
        return remainingCapacity;
    }

    public BigDecimal getUtilizationPercent() {
        return utilizationPercent;
    }
}
