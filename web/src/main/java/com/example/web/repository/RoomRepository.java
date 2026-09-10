package com.example.web.repository;

import com.example.web.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<RoomEntity, String> {
    List<RoomEntity> findByType(String type);
    List<RoomEntity> findByBuilding(String building);
}

