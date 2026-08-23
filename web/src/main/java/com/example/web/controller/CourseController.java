package com.example.web.controller;

import com.example.web.dto.CourseDTO;
import com.example.web.entity.CourseDesignationEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseDesignationRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
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

    @Autowired
    private CourseDesignationRepository courseDesignationRepository;

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
     * Every valid designation value (course_designation lookup table), for the
     * UI to populate a dropdown - not just values already in use by some
     * course, so a genuinely new designation can still be selected.
     * component_block_rule (preferred block size / max blocks per day, see
     * BlockGenerationService and SchoolConstraintProvider) is keyed by this
     * same value, exact and case-sensitive, so reusing one from this list
     * instead of a free-typed value avoids silently creating an orphaned,
     * unconfigured category. course.designation has an FK into this same
     * table, so this list is always exactly the set of values a course can
     * legally have.
     */
    @GetMapping("/designations")
    public List<String> getDesignations() {
        return courseDesignationRepository.findAll().stream()
                .map(CourseDesignationEntity::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
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
        course.setDesignation(request.getDesignation());
        course.setRoomRequirement(request.getRoomRequirement());
        course.setRequiredHoursPerWeek(request.getRequiredHoursPerWeek());
        if (request.getActive() != null) {
            course.setActive(request.getActive());
        }
    }
}
