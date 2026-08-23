package com.example.web.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping of the course_block_assignment_current view: the
 * resolved "current schedule" for each assignment (pinned rows keep their
 * own input timeslot; every other row resolves to the most recent
 * schedule_run's result). Used for display (Schedule View, reports) - never
 * written to. Domain CRUD still goes through CourseBlockAssignmentEntity /
 * course_block_assignment directly, which stays pure input.
 */
@Entity
@Immutable
@Table(name = "course_block_assignment_current")
public class CourseBlockAssignmentCurrentEntity {

    public CourseBlockAssignmentCurrentEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public CourseBlockAssignmentCurrentEntity(String id, String groupId, String courseId, Integer blockLength,
            Boolean pinned, String teacherId, String blockTimeslotId, String roomName, String satisfiesRoomType,
            String preferredRoomHint) {
        this.id = id;
        this.groupId = groupId;
        this.courseId = courseId;
        this.blockLength = blockLength;
        this.pinned = pinned;
        this.teacherId = teacherId;
        this.blockTimeslotId = blockTimeslotId;
        this.roomName = roomName;
        this.satisfiesRoomType = satisfiesRoomType;
        this.preferredRoomHint = preferredRoomHint;
    }

    @Id
    @Column(name = "id", length = 100)
    private String id;

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

    @Column(name = "block_timeslot_id", length = 50)
    private String blockTimeslotId;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "satisfies_room_type", length = 100)
    private String satisfiesRoomType;

    @Column(name = "preferred_room_hint", length = 100)
    private String preferredRoomHint;

    public String getId() {
        return id;
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

    public String getBlockTimeslotId() {
        return blockTimeslotId;
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
}
