package com.example.web.controller;

import com.example.web.entity.CourseEntity;
import com.example.web.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<CourseEntity> getCourseById(@PathVariable String id) {
        return courseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
    public CourseEntity createCourse(@RequestBody CourseEntity course) {
        return courseRepository.save(course);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<CourseEntity> updateCourse(@PathVariable String id, @RequestBody CourseEntity courseDetails) {
        return courseRepository.findById(id)
                .map(course -> {
                    course.setName(courseDetails.getName());
                    course.setAbbreviation(courseDetails.getAbbreviation());
                    course.setSemester(courseDetails.getSemester());
                    course.setComponent(courseDetails.getComponent());
                    course.setRoomRequirement(courseDetails.getRoomRequirement());
                    course.setRequiredHoursPerWeek(courseDetails.getRequiredHoursPerWeek());
                    course.setActive(courseDetails.getActive());
                    return ResponseEntity.ok(courseRepository.save(course));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable String id) {
        return courseRepository.findById(id)
                .map(course -> {
                    courseRepository.delete(course);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

