package com.example.web.repository;

import com.example.web.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for application users. The primary key is the username.
 */
public interface AppUserRepository extends JpaRepository<AppUserEntity, String> {
}
