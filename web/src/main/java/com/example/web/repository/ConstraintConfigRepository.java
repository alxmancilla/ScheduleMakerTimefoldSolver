package com.example.web.repository;

import com.example.web.entity.ConstraintConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstraintConfigRepository extends JpaRepository<ConstraintConfigEntity, String> {
}
