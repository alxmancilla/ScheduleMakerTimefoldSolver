package com.example.web.repository;

import com.example.web.entity.TeacherAvailabilityGridEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries against v_teacher_availability_grid. See
 * TeacherAvailabilityGridEntity.
 */
@Repository
public interface TeacherAvailabilityGridRepository extends JpaRepository<TeacherAvailabilityGridEntity, String> {
}
