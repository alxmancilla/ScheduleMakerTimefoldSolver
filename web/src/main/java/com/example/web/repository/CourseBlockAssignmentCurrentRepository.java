package com.example.web.repository;

import com.example.web.entity.CourseBlockAssignmentCurrentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only queries against course_block_assignment_current (the resolved
 * "current schedule" view). See CourseBlockAssignmentCurrentEntity.
 */
@Repository
public interface CourseBlockAssignmentCurrentRepository extends JpaRepository<CourseBlockAssignmentCurrentEntity, String> {

    List<CourseBlockAssignmentCurrentEntity> findByGroupId(String groupId);

    List<CourseBlockAssignmentCurrentEntity> findByTeacherId(String teacherId);

    List<CourseBlockAssignmentCurrentEntity> findByRoomName(String roomName);

    @Query("SELECT a FROM CourseBlockAssignmentCurrentEntity a WHERE a.blockTimeslotId IS NOT NULL")
    List<CourseBlockAssignmentCurrentEntity> findAssignedBlocks();

    @Query("SELECT a FROM CourseBlockAssignmentCurrentEntity a WHERE a.blockTimeslotId IS NULL")
    List<CourseBlockAssignmentCurrentEntity> findUnassignedBlocks();
}
