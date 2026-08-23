package com.example.web.repository;

import com.example.web.entity.CourseDesignationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseDesignationRepository extends JpaRepository<CourseDesignationEntity, String> {
}
