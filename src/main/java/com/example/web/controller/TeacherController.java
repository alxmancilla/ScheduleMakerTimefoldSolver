package com.example.web.controller;

import com.example.web.entity.TeacherEntity;
import com.example.web.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {
    
    @Autowired
    private TeacherRepository teacherRepository;
    
    @GetMapping
    public List<TeacherEntity> getAllTeachers() {
        return teacherRepository.findAll();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TeacherEntity> getTeacherById(@PathVariable String id) {
        return teacherRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/search")
    public List<TeacherEntity> searchTeachers(@RequestParam String query) {
        return teacherRepository.findByNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }
    
    @PostMapping
    public TeacherEntity createTeacher(@RequestBody TeacherEntity teacher) {
        return teacherRepository.save(teacher);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<TeacherEntity> updateTeacher(@PathVariable String id, @RequestBody TeacherEntity teacherDetails) {
        return teacherRepository.findById(id)
                .map(teacher -> {
                    teacher.setName(teacherDetails.getName());
                    teacher.setLastName(teacherDetails.getLastName());
                    teacher.setMaxHoursPerWeek(teacherDetails.getMaxHoursPerWeek());
                    return ResponseEntity.ok(teacherRepository.save(teacher));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeacher(@PathVariable String id) {
        return teacherRepository.findById(id)
                .map(teacher -> {
                    teacherRepository.delete(teacher);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

