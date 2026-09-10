package com.example.web.controller;

import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.BlockTimeslotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only timeslot listing for any authenticated role (READER/WRITER/ADMIN),
 * e.g. to populate a "block timeslot" dropdown when editing an assignment.
 * Mutating timeslots is admin-only; see TimeslotController at
 * /api/admin/timeslots.
 */
@RestController
@RequestMapping("/api/timeslots")
public class TimeslotReadController {

    @Autowired
    private BlockTimeslotRepository timeslotRepository;

    @GetMapping
    public List<BlockTimeslotEntity> getAllTimeslots() {
        return timeslotRepository.findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc();
    }

    @GetMapping("/{id}")
    public BlockTimeslotEntity getTimeslotById(@PathVariable String id) {
        return timeslotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", id));
    }
}
