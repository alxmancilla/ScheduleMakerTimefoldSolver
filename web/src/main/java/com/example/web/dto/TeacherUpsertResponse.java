package com.example.web.dto;

import com.example.web.entity.TeacherEntity;

import java.util.List;

/**
 * Response for POST /api/teachers and PUT /api/teachers/{id}: the saved
 * teacher, plus a non-blocking capacity warning when their total assigned
 * course hours (course_block_assignment.block_length, regardless of
 * solved/pinned status - same basis as v_teacher_workload) exceed their
 * total weekly availability (the count of teacher_availability rows just
 * saved). The save always succeeds when this is returned - there is no
 * blocking guardrail here, since an over-capacity teacher is often a
 * transient state the admin is actively fixing (e.g. widening availability
 * one day at a time), not invalid data.
 *
 * See TeacherController.buildCapacityWarning() for why this check exists:
 * nothing else in the system compares a teacher's total workload against
 * their availability before a solve - the solver was previously the only
 * thing that ever "noticed", and only indirectly, as double-booking hard
 * violations in the output.
 */
public class TeacherUpsertResponse {

    private final TeacherEntity teacher;
    private final List<String> warnings;

    public TeacherUpsertResponse(TeacherEntity teacher, List<String> warnings) {
        this.teacher = teacher;
        this.warnings = warnings;
    }

    public TeacherEntity getTeacher() {
        return teacher;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
