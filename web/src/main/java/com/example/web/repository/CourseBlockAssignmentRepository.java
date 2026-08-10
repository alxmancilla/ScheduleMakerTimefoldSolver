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

    @Query("SELECT a FROM CourseBlockAssignmentEntity a WHERE a.blockTimeslotId IS NOT NULL")
    List<CourseBlockAssignmentEntity> findAssignedBlocks();

    @Query("SELECT a FROM CourseBlockAssignmentEntity a WHERE a.blockTimeslotId IS NULL")
    List<CourseBlockAssignmentEntity> findUnassignedBlocks();
}
