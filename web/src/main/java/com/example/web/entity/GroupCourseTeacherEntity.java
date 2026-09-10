package com.example.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Read-only mapping of the v_group_course_teachers view: one row per
 * (group, course, teacher) - a course whose blocks are split across more
 * than one teacher produces more than one row for the same (group, course)
 * pair, which is why teacherId is part of the composite key rather than
 * (group, course) alone (confirmed empirically: at least one such split
 * exists in the live dataset). teacherId can be null (a group_course pair
 * with no blocks generated yet at all falls through the view's LEFT JOINs
 * to a single teacherless row).
 *
 * Backs GET /api/course-coverage: a course-coverage dashboard showing, per
 * (group, course), how many hours are actually scheduled against how many
 * are required, and a scheduling_status of "Complete" / "Partial" /
 * "Not Scheduled" computed by the view itself.
 *
 * block_lengths (a Postgres integer[] in the view) is deliberately not
 * mapped - scheduledTimeslots already carries the same information in a
 * directly displayable, human-readable form, so mapping the array type
 * (which needs its own Hibernate array-type handling) isn't worth it here.
 */
@Entity
@Immutable
@Table(name = "v_group_course_teachers")
@IdClass(GroupCourseTeacherEntity.Key.class)
public class GroupCourseTeacherEntity {

    @Id
    @Column(name = "group_id", length = 100)
    private String groupId;

    @Column(name = "group_name", length = 200)
    private String groupName;

    @Id
    @Column(name = "course_id", length = 100)
    private String courseId;

    @Column(name = "course_name", length = 200)
    private String courseName;

    @Column(name = "course_abbreviation", length = 100)
    private String courseAbbreviation;

    @Column(name = "required_hours_per_week")
    private Integer requiredHoursPerWeek;

    @Column(name = "semester")
    private Integer semester;

    @Column(name = "designation", length = 20)
    private String designation;

    @Column(name = "room_requirement", length = 50)
    private String roomRequirement;

    @Id
    @Column(name = "teacher_id", length = 100)
    private String teacherId;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "total_block_assignments")
    private Long totalBlockAssignments;

    @Column(name = "scheduled_hours")
    private Long scheduledHours;

    @Column(name = "scheduled_timeslots")
    private String scheduledTimeslots;

    @Column(name = "assigned_rooms")
    private String assignedRooms;

    @Column(name = "scheduling_status", length = 20)
    private String schedulingStatus;

    public GroupCourseTeacherEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public GroupCourseTeacherEntity(String groupId, String groupName, String courseId, String courseName,
            String courseAbbreviation, Integer requiredHoursPerWeek, Integer semester, String designation,
            String roomRequirement, String teacherId, String teacherName, Long totalBlockAssignments,
            Long scheduledHours, String scheduledTimeslots, String assignedRooms, String schedulingStatus) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.courseId = courseId;
        this.courseName = courseName;
        this.courseAbbreviation = courseAbbreviation;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
        this.semester = semester;
        this.designation = designation;
        this.roomRequirement = roomRequirement;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.totalBlockAssignments = totalBlockAssignments;
        this.scheduledHours = scheduledHours;
        this.scheduledTimeslots = scheduledTimeslots;
        this.assignedRooms = assignedRooms;
        this.schedulingStatus = schedulingStatus;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getCourseAbbreviation() {
        return courseAbbreviation;
    }

    public Integer getRequiredHoursPerWeek() {
        return requiredHoursPerWeek;
    }

    public Integer getSemester() {
        return semester;
    }

    public String getDesignation() {
        return designation;
    }

    public String getRoomRequirement() {
        return roomRequirement;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public Long getTotalBlockAssignments() {
        return totalBlockAssignments;
    }

    public Long getScheduledHours() {
        return scheduledHours;
    }

    public String getScheduledTimeslots() {
        return scheduledTimeslots;
    }

    public String getAssignedRooms() {
        return assignedRooms;
    }

    public String getSchedulingStatus() {
        return schedulingStatus;
    }

    public static class Key implements Serializable {
        private String groupId;
        private String courseId;
        private String teacherId;

        public Key() {
        }

        public Key(String groupId, String courseId, String teacherId) {
            this.groupId = groupId;
            this.courseId = courseId;
            this.teacherId = teacherId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(groupId, key.groupId) && Objects.equals(courseId, key.courseId)
                    && Objects.equals(teacherId, key.teacherId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, courseId, teacherId);
        }
    }
}
