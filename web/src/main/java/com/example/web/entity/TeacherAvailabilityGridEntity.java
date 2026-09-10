package com.example.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping of the v_teacher_availability_grid view: one row per
 * teacher, with each weekday's available hours pre-aggregated server-side
 * into a comma-separated string (e.g. "7, 8, 9, 10, 11") - condenses what
 * would otherwise be a per-teacher, per-day, per-hour join into one row per
 * teacher, so the frontend can render a compact weekly grid without pulling
 * the raw teacher_availability rows.
 *
 * Backs TeacherController's GET /api/teachers/availability-grid.
 */
@Entity
@Immutable
@Table(name = "v_teacher_availability_grid")
public class TeacherAvailabilityGridEntity {

    @Id
    @Column(name = "teacher_id", length = 100)
    private String teacherId;

    @Column(name = "teacher_full_name")
    private String teacherFullName;

    @Column(name = "monday_hours")
    private String mondayHours;

    @Column(name = "tuesday_hours")
    private String tuesdayHours;

    @Column(name = "wednesday_hours")
    private String wednesdayHours;

    @Column(name = "thursday_hours")
    private String thursdayHours;

    @Column(name = "friday_hours")
    private String fridayHours;

    @Column(name = "saturday_hours")
    private String saturdayHours;

    @Column(name = "sunday_hours")
    private String sundayHours;

    public TeacherAvailabilityGridEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public TeacherAvailabilityGridEntity(String teacherId, String teacherFullName, String mondayHours,
            String tuesdayHours, String wednesdayHours, String thursdayHours, String fridayHours,
            String saturdayHours, String sundayHours) {
        this.teacherId = teacherId;
        this.teacherFullName = teacherFullName;
        this.mondayHours = mondayHours;
        this.tuesdayHours = tuesdayHours;
        this.wednesdayHours = wednesdayHours;
        this.thursdayHours = thursdayHours;
        this.fridayHours = fridayHours;
        this.saturdayHours = saturdayHours;
        this.sundayHours = sundayHours;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getTeacherFullName() {
        return teacherFullName;
    }

    public String getMondayHours() {
        return mondayHours;
    }

    public String getTuesdayHours() {
        return tuesdayHours;
    }

    public String getWednesdayHours() {
        return wednesdayHours;
    }

    public String getThursdayHours() {
        return thursdayHours;
    }

    public String getFridayHours() {
        return fridayHours;
    }

    public String getSaturdayHours() {
        return saturdayHours;
    }

    public String getSundayHours() {
        return sundayHours;
    }
}
