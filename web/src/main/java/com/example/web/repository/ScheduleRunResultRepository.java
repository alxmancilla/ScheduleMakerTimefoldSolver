package com.example.web.repository;

import com.example.web.entity.ScheduleRunResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRunResultRepository
        extends JpaRepository<ScheduleRunResultEntity, ScheduleRunResultEntity.Key> {
    List<ScheduleRunResultEntity> findByScheduleRunId(Integer scheduleRunId);
}
