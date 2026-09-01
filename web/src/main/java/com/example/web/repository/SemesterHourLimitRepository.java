package com.example.web.repository;

import com.example.web.entity.SemesterHourLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SemesterHourLimitRepository extends JpaRepository<SemesterHourLimitEntity, Integer> {
}
