package com.example.web.controller;

import com.example.web.dto.TimeslotDTO;
import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only CRUD for the block timeslot grid (day/start hour/length) that
 * courses get scheduled into. Mounted under /api/admin/**, which
 * SecurityConfig already restricts to the ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/timeslots")
public class TimeslotController {

    @Autowired
    private BlockTimeslotRepository timeslotRepository;

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @GetMapping
    public List<BlockTimeslotEntity> getAllTimeslots() {
        return timeslotRepository.findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc();
    }

    @GetMapping("/{id}")
    public BlockTimeslotEntity getTimeslotById(@PathVariable String id) {
        return timeslotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", id));
    }

    @PostMapping
    public BlockTimeslotEntity createTimeslot(@Valid @RequestBody TimeslotDTO request) {
        if (timeslotRepository.existsByDayOfWeekAndStartHourAndLengthHours(
                request.getDayOfWeek(), request.getStartHour(), request.getLengthHours())) {
            throw new IllegalArgumentException("A timeslot with this day, start hour, and length already exists");
        }
        BlockTimeslotEntity timeslot = new BlockTimeslotEntity(
                request.getDayOfWeek(), request.getStartHour(), request.getLengthHours());
        timeslot.setId("block_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        return timeslotRepository.save(timeslot);
    }

    @PutMapping("/{id}")
    public BlockTimeslotEntity updateTimeslot(@PathVariable String id, @Valid @RequestBody TimeslotDTO request) {
        BlockTimeslotEntity timeslot = timeslotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", id));
        if (timeslotRepository.existsByDayOfWeekAndStartHourAndLengthHoursAndIdNot(
                request.getDayOfWeek(), request.getStartHour(), request.getLengthHours(), id)) {
            throw new IllegalArgumentException("A timeslot with this day, start hour, and length already exists");
        }
        timeslot.setDayOfWeek(request.getDayOfWeek());
        timeslot.setStartHour(request.getStartHour());
        timeslot.setLengthHours(request.getLengthHours());
        return timeslotRepository.save(timeslot);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTimeslot(@PathVariable String id) {
        BlockTimeslotEntity timeslot = timeslotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", id));
        long usageCount = assignmentRepository.countByBlockTimeslotId(id);
        if (usageCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete: this timeslot is used by " + usageCount + " assignment(s). Reassign or unpin them first.");
        }
        timeslotRepository.delete(timeslot);
        return ResponseEntity.noContent().build();
    }
}
