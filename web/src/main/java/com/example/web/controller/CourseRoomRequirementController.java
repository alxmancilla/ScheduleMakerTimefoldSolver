package com.example.web.controller;

import com.example.web.dto.CourseRoomRequirementDTO;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import com.example.web.repository.RoomRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for a course's dual room requirements (course_room_requirement): a
 * course that needs its hours split across multiple room types (e.g. 4h in a
 * computer center + 1h in a standard room) gets one row per room type here,
 * instead of - or alongside - the single legacy CourseEntity.roomRequirement
 * field. Nested under /api/courses/{courseId}/room-requirements since a
 * requirement only ever makes sense in the context of its course; falls under
 * the general /api/** GET/POST/PUT/DELETE role rules like the rest of
 * /api/courses/**, no ADMIN-only restriction.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/room-requirements")
public class CourseRoomRequirementController {

    @Autowired
    private CourseRoomRequirementRepository requirementRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RoomRepository roomRepository;

    @GetMapping
    public List<CourseRoomRequirementEntity> getRequirements(@PathVariable String courseId) {
        requireCourse(courseId);
        return requirementRepository.findByCourseIdOrderByPriority(courseId);
    }

    @PostMapping
    public CourseRoomRequirementEntity createRequirement(@PathVariable String courseId,
            @Valid @RequestBody CourseRoomRequirementDTO request) {
        requireCourse(courseId);
        validatePreferredRoom(request.getDefaultPreferredRoom());
        CourseRoomRequirementEntity requirement = new CourseRoomRequirementEntity(
                courseId, request.getRoomType(), request.getHoursRequired(),
                request.getPriority() != null ? request.getPriority() : 1,
                request.getDefaultPreferredRoom());
        return requirementRepository.save(requirement);
    }

    @PutMapping("/{id}")
    public CourseRoomRequirementEntity updateRequirement(@PathVariable String courseId, @PathVariable Long id,
            @Valid @RequestBody CourseRoomRequirementDTO request) {
        CourseRoomRequirementEntity requirement = requireRequirement(courseId, id);
        validatePreferredRoom(request.getDefaultPreferredRoom());
        requirement.setRoomType(request.getRoomType());
        requirement.setHoursRequired(request.getHoursRequired());
        requirement.setPriority(request.getPriority() != null ? request.getPriority() : 1);
        requirement.setDefaultPreferredRoom(request.getDefaultPreferredRoom());
        return requirementRepository.save(requirement);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRequirement(@PathVariable String courseId, @PathVariable Long id) {
        CourseRoomRequirementEntity requirement = requireRequirement(courseId, id);
        requirementRepository.delete(requirement);
        return ResponseEntity.noContent().build();
    }

    private void requireCourse(String courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }
    }

    private CourseRoomRequirementEntity requireRequirement(String courseId, Long id) {
        requireCourse(courseId);
        CourseRoomRequirementEntity requirement = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room requirement", String.valueOf(id)));
        if (!requirement.getCourseId().equals(courseId)) {
            throw new ResourceNotFoundException("Room requirement", String.valueOf(id));
        }
        return requirement;
    }

    private void validatePreferredRoom(String roomName) {
        if (roomName != null && !roomRepository.existsById(roomName)) {
            throw new IllegalArgumentException("Room '" + roomName + "' does not exist");
        }
    }
}
