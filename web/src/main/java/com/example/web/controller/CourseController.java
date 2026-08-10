package com.example.web.controller;

import com.example.web.dto.CourseDTO;
import com.example.web.entity.CourseEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseRepository;
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
