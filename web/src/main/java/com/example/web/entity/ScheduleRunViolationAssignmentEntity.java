package com.example.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;

/**
 * Which course_block_assignment(s) one schedule_run_violation row is about -
 * see DataSaver (engine module) for how these are written, from each
 * ViolationInstance's own assignmentIds. Lets the web Schedule view link a
 * persisted violation back to the specific grid card(s) it involves, instead
 * of only showing it as a separate description in a list. Read-only from the
 * web side, same as ScheduleRunViolationEntity: nothing here ever
 * inserts/updates this table. assignmentId is a plain label, not a live FK
 * (see the migration's own comment) - it survives the assignment being
 * deleted or regenerated later, so it's not mapped to CourseBlockAssignmentEntity.
 */
@Entity
@Immutable
@Table(name = "schedule_run_violation_assignment")
@IdClass(ScheduleRunViolationAssignmentEntity.Key.class)
public class ScheduleRunViolationAssignmentEntity {

    public ScheduleRunViolationAssignmentEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public ScheduleRunViolationAssignmentEntity(Long violationId, String assignmentId) {
        this.violationId = violationId;
        this.assignmentId = assignmentId;
    }

    @Id
    @Column(name = "violation_id")
    private Long violationId;

    @Id
    @Column(name = "assignment_id", length = 100)
    private String assignmentId;

    public Long getViolationId() {
        return violationId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public static class Key implements Serializable {
        private Long violationId;
        private String assignmentId;

        public Key() {
        }

        public Key(Long violationId, String assignmentId) {
            this.violationId = violationId;
            this.assignmentId = assignmentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Key key = (Key) o;
            return violationId.equals(key.violationId) && assignmentId.equals(key.assignmentId);
        }

        @Override
        public int hashCode() {
            return violationId.hashCode() + assignmentId.hashCode();
        }
    }
}
