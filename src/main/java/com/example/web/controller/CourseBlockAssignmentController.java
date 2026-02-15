package com.example.web.controller;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class CourseBlockAssignmentController {

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @GetMapping
    public List<CourseBlockAssignmentEntity> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseBlockAssignmentEntity> getAssignmentById(@PathVariable String id) {
        return assignmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/group/{groupId}")
    public List<CourseBlockAssignmentEntity> getAssignmentsByGroup(@PathVariable String groupId) {
        return assignmentRepository.findByGroupId(groupId);
    }

    @GetMapping("/teacher/{teacherId}")
    public List<CourseBlockAssignmentEntity> getAssignmentsByTeacher(@PathVariable String teacherId) {
        return assignmentRepository.findByTeacherId(teacherId);
    }

    @GetMapping("/room/{roomName}")
    public List<CourseBlockAssignmentEntity> getAssignmentsByRoom(@PathVariable String roomName) {
        return assignmentRepository.findByRoomName(roomName);
    }

    @GetMapping("/assigned")
    public List<CourseBlockAssignmentEntity> getAssignedBlocks() {
        return assignmentRepository.findAssignedBlocks();
    }

    @GetMapping("/unassigned")
    public List<CourseBlockAssignmentEntity> getUnassignedBlocks() {
        return assignmentRepository.findUnassignedBlocks();
    }

    @GetMapping("/pinned")
    public List<CourseBlockAssignmentEntity> getPinnedAssignments() {
        return assignmentRepository.findByPinned(true);
    }

    @PostMapping
    public CourseBlockAssignmentEntity createAssignment(@RequestBody CourseBlockAssignmentEntity assignment) {
        return assignmentRepository.save(assignment);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseBlockAssignmentEntity> updateAssignment(@PathVariable String id,
            @RequestBody CourseBlockAssignmentEntity assignmentDetails) {
        return assignmentRepository.findById(id)
                .map(assignment -> {
                    assignment.setGroupId(assignmentDetails.getGroupId());
                    assignment.setCourseId(assignmentDetails.getCourseId());
                    assignment.setBlockLength(assignmentDetails.getBlockLength());
                    assignment.setPinned(assignmentDetails.getPinned());
                    assignment.setTeacherId(assignmentDetails.getTeacherId());
                    assignment.setBlockTimeslotId(assignmentDetails.getBlockTimeslotId());
                    assignment.setRoomName(assignmentDetails.getRoomName());
                    assignment.setSatisfiesRoomType(assignmentDetails.getSatisfiesRoomType());
                    assignment.setPreferredRoomName(assignmentDetails.getPreferredRoomName());
                    return ResponseEntity.ok(assignmentRepository.save(assignment));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable String id) {
        return assignmentRepository.findById(id)
                .map(assignment -> {
                    assignmentRepository.delete(assignment);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
