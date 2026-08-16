package com.example.web.controller;

import com.example.web.dto.CourseDTO;
import com.example.web.entity.CourseEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @Autowired
    private CourseRoomRequirementRepository roomRequirementRepository;

    @GetMapping
    public List<CourseEntity> getAllCourses() {
        return courseRepository.findAll();
    }

    @GetMapping("/{id}")
    public CourseEntity getCourseById(@PathVariable String id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @GetMapping("/search")
    public List<CourseEntity> searchCourses(@RequestParam String query) {
        return courseRepository.findByNameContainingIgnoreCase(query);
    }

    @GetMapping("/active")
    public List<CourseEntity> getActiveCourses() {
        return courseRepository.findByActive(true);
    }

    /**
     * Distinct component values already in use, for the UI to offer as
     * autocomplete suggestions. The column is free text (not a DB enum) so new
     * values are still allowed, but constraint logic elsewhere does an exact,
     * case-sensitive match on specific values (e.g. "BASICAS" in
     * SchoolConstraintProvider) - reusing an existing value via autocomplete
     * avoids the silent constraint mismatch a typo would cause.
     */
    @GetMapping("/components")
    public List<String> getDistinctComponents() {
        return courseRepository.findDistinctComponents();
    }

    /**
     * IDs of courses that have dual room requirements configured
     * (course_room_requirement rows), for the Courses list/edit UI to flag
     * that a course's single legacy roomRequirement field is being ignored -
     * BlockGenerationService uses the dual requirements instead whenever any
     * exist for a course.
     */
    @GetMapping("/with-room-requirements")
    public List<String> getCourseIdsWithRoomRequirements() {
        return roomRequirementRepository.findDistinctCourseIds();
    }

    @PostMapping
    public CourseEntity createCourse(
            @Validated({ Default.class, CourseDTO.Create.class }) @RequestBody CourseDTO request) {
        if (courseRepository.existsById(request.getId())) {
            throw new IllegalArgumentException("Course with ID '" + request.getId() + "' already exists");
        }
        CourseEntity course = new CourseEntity();
        course.setId(request.getId());
        applyFields(course, request);
        return courseRepository.save(course);
    }

    @PutMapping("/{id}")
    public CourseEntity updateCourse(@PathVariable String id, @Valid @RequestBody CourseDTO request) {
        CourseEntity course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        applyFields(course, request);
        return courseRepository.save(course);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable String id) {
        CourseEntity course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        // course_block_assignment.course_id is ON DELETE CASCADE, so deleting the
        // course would silently delete every schedule block for it too - block
        // instead, same guard shape as TimeslotController.
        long usageCount = assignmentRepository.countByCourseId(id);
        if (usageCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete: this course has " + usageCount + " schedule block(s). Delete those assignments first.");
        }
        courseRepository.delete(course);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(CourseEntity course, CourseDTO request) {
        course.setName(request.getName());
        course.setAbbreviation(request.getAbbreviation());
        course.setSemester(request.getSemester());
        course.setComponent(request.getComponent());
        course.setRoomRequirement(request.getRoomRequirement());
        course.setRequiredHoursPerWeek(request.getRequiredHoursPerWeek());
        if (request.getActive() != null) {
            course.setActive(request.getActive());
        }
    }
}
