package com.example.web.repository;

import com.example.web.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<CourseEntity, String> {
    List<CourseEntity> findByNameContainingIgnoreCase(String name);
    List<CourseEntity> findByActive(Boolean active);
    Optional<CourseEntity> findByName(String name);
    boolean existsByName(String name);
}

