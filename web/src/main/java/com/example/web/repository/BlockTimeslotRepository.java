package com.example.web.repository;

import com.example.web.entity.BlockTimeslotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockTimeslotRepository extends JpaRepository<BlockTimeslotEntity, String> {
    List<BlockTimeslotEntity> findByDayOfWeek(Integer dayOfWeek);

    Optional<BlockTimeslotEntity> findByDayOfWeekAndStartHourAndLengthHours(
            Integer dayOfWeek, Integer startHour, Integer lengthHours);

    List<BlockTimeslotEntity> findByLengthHours(Integer lengthHours);

    List<BlockTimeslotEntity> findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc();

    boolean existsByDayOfWeekAndStartHourAndLengthHours(Integer dayOfWeek, Integer startHour, Integer lengthHours);

    boolean existsByDayOfWeekAndStartHourAndLengthHoursAndIdNot(
            Integer dayOfWeek, Integer startHour, Integer lengthHours, String id);
}
