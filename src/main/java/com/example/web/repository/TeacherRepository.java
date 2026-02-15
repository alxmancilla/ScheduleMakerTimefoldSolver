package com.example.web.repository;

import com.example.web.entity.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherRepository extends JpaRepository<TeacherEntity, String> {
    List<TeacherEntity> findByNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String name, String lastName);
}

