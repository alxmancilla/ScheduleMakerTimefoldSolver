package com.example.web.repository;

import com.example.web.entity.ScheduleRunViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRunViolationRepository extends JpaRepository<ScheduleRunViolationEntity, Long> {
    List<ScheduleRunViolationEntity> findByScheduleRunId(Integer scheduleRunId);
}
