package com.example.web.repository;

import com.example.web.entity.CalendarExceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalendarExceptionRepository extends JpaRepository<CalendarExceptionEntity, LocalDate> {
    List<CalendarExceptionEntity> findAllByOrderByExceptionDateAsc();
}
