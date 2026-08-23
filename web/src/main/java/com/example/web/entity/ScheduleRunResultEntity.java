package com.example.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.io.Serializable;
import java.util.Objects;

/**
 * One row per assignment per run: that run's solved (or still-unassigned)
 * timeslot, plus a frozen copy of the assignment's input fields at that
 * moment (group/course/blockLength/pinned/teacher/room/satisfiesRoomType/
 * preferredRoomHint). The group/course/teacher/room values are a snapshot,
 * not a live reference - a later rename or deletion of that teacher/room/
 * course/group must not alter a historical run's record. Composite key
 * matching the table's actual PRIMARY KEY (schedule_run_id, assignment_id) -
 * see ScheduleRunResultEntity.Key. Read-only from the web side.
 */
@Entity
@Immutable
@Table(name = "schedule_run_result")
@IdClass(ScheduleRunResultEntity.Key.class)
public class ScheduleRunResultEntity {

    public ScheduleRunResultEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public ScheduleRunResultEntity(Integer scheduleRunId, String assignmentId, String blockTimeslotId,
            String groupId, String courseId, Integer blockLength, Boolean pinned, String teacherId, String roomName,
            String satisfiesRoomType, String preferredRoomHint) {
        this.scheduleRunId = scheduleRunId;
        this.assignmentId = assignmentId;
        this.blockTimeslotId = blockTimeslotId;
        this.groupId = groupId;
        this.courseId = courseId;
        this.blockLength = blockLength;
        this.pinned = pinned;
        this.teacherId = teacherId;
        this.roomName = roomName;
        this.satisfiesRoomType = satisfiesRoomType;
        this.preferredRoomHint = preferredRoomHint;
    }

    @Id
    @Column(name = "schedule_run_id")
    private Integer scheduleRunId;

    @Id
    @Column(name = "assignment_id", length = 100)
    private String assignmentId;

    @Column(name = "block_timeslot_id", length = 50)
    private String blockTimeslotId;

    @Column(name = "group_id", length = 100)
    private String groupId;

    @Column(name = "course_id", length = 100)
    private String courseId;

    @Column(name = "block_length")
    private Integer blockLength;

    @Column(name = "pinned")
    private Boolean pinned;

    @Column(name = "teacher_id", length = 100)
    private String teacherId;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "satisfies_room_type", length = 100)
    private String satisfiesRoomType;

    @Column(name = "preferred_room_hint", length = 100)
    private String preferredRoomHint;

    public Integer getScheduleRunId() {
        return scheduleRunId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public String getBlockTimeslotId() {
        return blockTimeslotId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getCourseId() {
        return courseId;
    }

    public Integer getBlockLength() {
        return blockLength;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getSatisfiesRoomType() {
        return satisfiesRoomType;
    }

    public String getPreferredRoomHint() {
        return preferredRoomHint;
    }

    public static class Key implements Serializable {
        private Integer scheduleRunId;
        private String assignmentId;

        public Key() {
        }

        public Key(Integer scheduleRunId, String assignmentId) {
            this.scheduleRunId = scheduleRunId;
            this.assignmentId = assignmentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(scheduleRunId, key.scheduleRunId) && Objects.equals(assignmentId, key.assignmentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scheduleRunId, assignmentId);
        }
    }
}
