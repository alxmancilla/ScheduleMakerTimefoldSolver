package com.example.web.repository;

import com.example.web.entity.StudentGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentGroupRepository extends JpaRepository<StudentGroupEntity, String> {
    List<StudentGroupEntity> findByNameContainingIgnoreCase(String name);
}

