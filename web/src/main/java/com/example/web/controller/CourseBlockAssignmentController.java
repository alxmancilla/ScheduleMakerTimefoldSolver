package com.example.web.controller;

import com.example.web.dto.CourseBlockAssignmentDTO;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.common.RoomTypeCompatibility;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class CourseBlockAssignmentController {

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private RoomRepository roomRepository;

    @GetMapping
    public List<CourseBlockAssignmentEntity> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    @GetMapping("/{id}")
    public CourseBlockAssignmentEntity getAssignmentById(@PathVariable String id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
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
    public CourseBlockAssignmentEntity createAssignment(
            @Validated({ Default.class,
                    CourseBlockAssignmentDTO.Create.class }) @RequestBody CourseBlockAssignmentDTO request) {
        if (assignmentRepository.existsById(request.getId())) {
            throw new IllegalArgumentException("Assignment with ID '" + request.getId() + "' already exists");
        }
        CourseBlockAssignmentEntity assignment = new CourseBlockAssignmentEntity();
        assignment.setId(request.getId());
        applyFields(assignment, request);
        return assignmentRepository.save(assignment);
    }

    @PutMapping("/{id}")
    public CourseBlockAssignmentEntity updateAssignment(@PathVariable String id,
            @Valid @RequestBody CourseBlockAssignmentDTO request) {
        CourseBlockAssignmentEntity assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        applyFields(assignment, request);
        return assignmentRepository.save(assignment);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable String id) {
        CourseBlockAssignmentEntity assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        assignmentRepository.delete(assignment);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(CourseBlockAssignmentEntity assignment, CourseBlockAssignmentDTO request) {
        assignment.setGroupId(request.getGroupId());
        assignment.setCourseId(request.getCourseId());
        assignment.setBlockLength(request.getBlockLength());
        if (request.getPinned() != null) {
            assignment.setPinned(request.getPinned());
        }
        assignment.setTeacherId(request.getTeacherId());
        assignment.setBlockTimeslotId(request.getBlockTimeslotId());
        assignment.setRoomName(request.getRoomName());
        assignment.setSatisfiesRoomType(request.getSatisfiesRoomType());
        assignment.setPreferredRoomHint(request.getPreferredRoomHint());
        applyTeacherRequiredRoom(assignment);
    }

    /**
     * When this block's teacher has a required room, forces it onto roomName -
     * overriding whatever room was submitted, regardless of the group's
     * preferred room - as long as the room's type is compatible with this
     * block's satisfiesRoomType. Runs on every create/update, not just when
     * teacherId changes, so the requirement holds even if the room field alone
     * is edited afterward.
     */
    private void applyTeacherRequiredRoom(CourseBlockAssignmentEntity assignment) {
        if (assignment.getTeacherId() == null) {
            return;
        }
        TeacherEntity teacher = teacherRepository.findById(assignment.getTeacherId()).orElse(null);
        if (teacher == null || teacher.getRequiredRoomName() == null) {
            return;
        }
        RoomEntity requiredRoom = roomRepository.findById(teacher.getRequiredRoomName()).orElse(null);
        if (requiredRoom == null || !RoomTypeCompatibility.satisfies(requiredRoom.getType(), assignment.getSatisfiesRoomType())) {
            return;
        }
        assignment.setRoomName(teacher.getRequiredRoomName());
    }
}
