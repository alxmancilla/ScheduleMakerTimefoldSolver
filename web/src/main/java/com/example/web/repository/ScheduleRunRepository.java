package com.example.web.repository;

import com.example.web.entity.ScheduleRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRunRepository extends JpaRepository<ScheduleRunEntity, Integer> {
    List<ScheduleRunEntity> findAllByOrderByCreatedAtDesc();
}
