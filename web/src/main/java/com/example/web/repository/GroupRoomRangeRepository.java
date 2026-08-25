package com.example.web.repository;

import com.example.web.entity.GroupRoomRangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRoomRangeRepository extends JpaRepository<GroupRoomRangeEntity, Long> {
    List<GroupRoomRangeEntity> findByGroupIdOrderByRoomType(String groupId);

    List<GroupRoomRangeEntity> findByGroupIdAndRoomType(String groupId, String roomType);
}
