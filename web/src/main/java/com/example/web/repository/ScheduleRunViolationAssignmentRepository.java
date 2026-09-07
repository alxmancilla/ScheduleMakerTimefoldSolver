package com.example.web.repository;

import com.example.web.entity.ScheduleRunViolationAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRunViolationAssignmentRepository
        extends JpaRepository<ScheduleRunViolationAssignmentEntity, ScheduleRunViolationAssignmentEntity.Key> {
    List<ScheduleRunViolationAssignmentEntity> findByViolationIdIn(List<Long> violationIds);
}
