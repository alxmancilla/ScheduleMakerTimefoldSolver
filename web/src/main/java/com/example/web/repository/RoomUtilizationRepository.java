package com.example.web.repository;

import com.example.web.entity.RoomUtilizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries against v_room_utilization. See RoomUtilizationEntity.
 */
@Repository
public interface RoomUtilizationRepository extends JpaRepository<RoomUtilizationEntity, String> {
}
