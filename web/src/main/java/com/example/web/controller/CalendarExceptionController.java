package com.example.web.controller;

import com.example.web.dto.CalendarExceptionDTO;
import com.example.web.entity.CalendarExceptionEntity;
import com.example.web.repository.CalendarExceptionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin-only management of the school's calendar exceptions (holidays, exam
 * days, half-days). Mounted under /api/admin/**, which SecurityConfig
 * already restricts to the ADMIN role. Keyed by exception_date (a natural
 * key), so PUT upserts rather than requiring a separate create/update
 * distinction - same convention as ComponentBlockRuleController.
 *
 * Record-keeping only (v1): this data is not yet read by block generation or
 * the solver. See the migration comment in
 * database/migrations/add_calendar_exception.sql for why.
 */
@RestController
@RequestMapping("/api/admin/calendar-exceptions")
public class CalendarExceptionController {

    @Autowired
    private CalendarExceptionRepository exceptionRepository;

    @GetMapping
    public List<CalendarExceptionEntity> getAllExceptions() {
        return exceptionRepository.findAllByOrderByExceptionDateAsc();
    }

    @PutMapping("/{date}")
    public CalendarExceptionEntity upsertException(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CalendarExceptionDTO request) {
        if ("HALF_DAY".equals(request.getType()) && request.getEndHour() == null) {
            throw new IllegalArgumentException("End hour is required for a half-day exception");
        }
        CalendarExceptionEntity exception = exceptionRepository.findById(date)
                .orElseGet(() -> new CalendarExceptionEntity(date, null, null, null));
        exception.setType(request.getType());
        exception.setLabel(request.getLabel());
        exception.setEndHour("HALF_DAY".equals(request.getType()) ? request.getEndHour() : null);
        return exceptionRepository.save(exception);
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteException(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (exceptionRepository.existsById(date)) {
            exceptionRepository.deleteById(date);
        }
        return ResponseEntity.noContent().build();
    }
}
