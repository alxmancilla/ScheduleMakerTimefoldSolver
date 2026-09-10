package com.example.web.repository;

import com.example.web.entity.SchoolTermEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolTermRepository extends JpaRepository<SchoolTermEntity, Integer> {
}
