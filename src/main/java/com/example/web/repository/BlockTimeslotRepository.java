package com.example.web.repository;

import com.example.web.entity.BlockTimeslotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlockTimeslotRepository extends JpaRepository<BlockTimeslotEntity, String> {
    List<BlockTimeslotEntity> findByDayOfWeek(Integer dayOfWeek);

    List<BlockTimeslotEntity> findByLengthHours(Integer lengthHours);
}
