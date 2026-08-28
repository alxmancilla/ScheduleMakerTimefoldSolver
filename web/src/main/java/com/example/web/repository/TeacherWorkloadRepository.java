package com.example.web.repository;

import com.example.web.entity.TeacherWorkloadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries against v_teacher_workload. See TeacherWorkloadEntity.
 */
@Repository
public interface TeacherWorkloadRepository extends JpaRepository<TeacherWorkloadEntity, String> {
}
