package com.example.web.repository;

import com.example.web.entity.CourseRoomRequirementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRoomRequirementRepository extends JpaRepository<CourseRoomRequirementEntity, Long> {
    List<CourseRoomRequirementEntity> findByCourseIdOrderByPriority(String courseId);

    @Query("SELECT DISTINCT r.courseId FROM CourseRoomRequirementEntity r")
    List<String> findDistinctCourseIds();
}
