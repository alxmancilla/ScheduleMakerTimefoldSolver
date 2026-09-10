package com.example.web.repository;

import com.example.web.entity.CourseBlockAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseBlockAssignmentRepository extends JpaRepository<CourseBlockAssignmentEntity, String> {
    List<CourseBlockAssignmentEntity> findByGroupId(String groupId);

    List<CourseBlockAssignmentEntity> findByTeacherId(String teacherId);

    List<CourseBlockAssignmentEntity> findByRoomName(String roomName);

    List<CourseBlockAssignmentEntity> findByPinned(Boolean pinned);

    long countByBlockTimeslotId(String blockTimeslotId);

    long countByCourseId(String courseId);

    long countByGroupId(String groupId);

    boolean existsByGroupIdAndCourseId(String groupId, String courseId);

    @Query("SELECT a FROM CourseBlockAssignmentEntity a WHERE a.blockTimeslotId IS NOT NULL")
    List<CourseBlockAssignmentEntity> findAssignedBlocks();

    @Query("SELECT a FROM CourseBlockAssignmentEntity a WHERE a.blockTimeslotId IS NULL")
    List<CourseBlockAssignmentEntity> findUnassignedBlocks();

    /**
     * Guardrail #2 for SemesterHourLimitController: every PINNED block of a
     * course in {@code semester} whose timeslot already ends after
     * {@code latestEndHour} - an exact (not heuristic) check, since a pinned
     * row is real, already-committed data, not a projection. Native query
     * because CourseBlockAssignmentEntity has no JPA relations to Course/
     * BlockTimeslot to join through (plain FK string columns only).
     */
    @Query(value = "SELECT g.name AS groupName, c.name AS courseName, "
            + "bt.day_of_week AS dayOfWeek, bt.start_hour AS startHour, bt.length_hours AS lengthHours "
            + "FROM course_block_assignment cba "
            + "JOIN course c ON c.id = cba.course_id "
            + "JOIN block_timeslot bt ON bt.id = cba.block_timeslot_id "
            + "LEFT JOIN student_group g ON g.id = cba.group_id "
            + "WHERE c.semester = :semester AND cba.pinned = true "
            + "AND (bt.start_hour + bt.length_hours) > :latestEndHour "
            + "ORDER BY g.name, c.name", nativeQuery = true)
    List<PinnedHourLimitViolation> findPinnedHourLimitViolations(int semester, int latestEndHour);

    /**
     * Guardrail #3 for SemesterHourLimitController: each group's total
     * weekly block-hours among courses in {@code semester} - the same
     * per-group demand figure used throughout this project's manual
     * semester-capacity analysis (e.g. "24h needed vs 35h available" for
     * semester 1), now computed here to warn instead of requiring a human
     * to run the query by hand.
     */
    @Query(value = "SELECT cba.group_id AS groupId, SUM(cba.block_length) AS totalHours "
            + "FROM course_block_assignment cba "
            + "JOIN course c ON c.id = cba.course_id "
            + "WHERE c.semester = :semester "
            + "GROUP BY cba.group_id", nativeQuery = true)
    List<GroupSemesterDemand> findGroupWeeklyDemandForSemester(int semester);

    interface PinnedHourLimitViolation {
        String getGroupName();

        String getCourseName();

        Integer getDayOfWeek();

        Integer getStartHour();

        Integer getLengthHours();
    }

    interface GroupSemesterDemand {
        String getGroupId();

        Integer getTotalHours();
    }
}
