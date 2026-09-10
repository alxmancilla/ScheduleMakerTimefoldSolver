package com.example.web.repository;

import com.example.web.entity.GroupCourseTeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries against v_group_course_teachers. See
 * GroupCourseTeacherEntity.
 */
@Repository
public interface GroupCourseTeacherRepository
        extends JpaRepository<GroupCourseTeacherEntity, GroupCourseTeacherEntity.Key> {
}
