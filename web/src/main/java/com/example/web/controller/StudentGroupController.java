package com.example.web.controller;

import com.example.common.RoomTypeCompatibility;
import com.example.web.dto.StudentGroupDTO;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class StudentGroupController {

    @Autowired
    private StudentGroupRepository groupRepository;

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @Autowired
    private RoomRepository roomRepository;

    @GetMapping
    public List<StudentGroupEntity> getAllGroups() {
        return groupRepository.findAll();
    }

    @GetMapping("/{id}")
    public StudentGroupEntity getGroupById(@PathVariable String id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }

    @GetMapping("/search")
    public List<StudentGroupEntity> searchGroups(@RequestParam String query) {
        return groupRepository.findByNameContainingIgnoreCase(query);
    }

    @PostMapping
    public StudentGroupEntity createGroup(
            @Validated({ Default.class, StudentGroupDTO.Create.class }) @RequestBody StudentGroupDTO request) {
        if (groupRepository.existsById(request.getId())) {
            throw new IllegalArgumentException("Group with ID '" + request.getId() + "' already exists");
        }
        StudentGroupEntity group = new StudentGroupEntity(request.getId(), request.getName());
        group.setPreferredRoomName(request.getPreferredRoomName());
        group.setStudentCount(request.getStudentCount());
        return groupRepository.save(group);
    }

    @PutMapping("/{id}")
    @Transactional
    public StudentGroupEntity updateGroup(@PathVariable String id, @Valid @RequestBody StudentGroupDTO request) {
        StudentGroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
        group.setName(request.getName());
        group.setPreferredRoomName(request.getPreferredRoomName());
        group.setStudentCount(request.getStudentCount());
        StudentGroupEntity saved = groupRepository.save(group);
        backfillPreferredRoom(saved);
        return saved;
    }

    /**
     * When a group has a preferred room, forces it onto every existing
     * (non-pinned) block already assigned to that group whose room type is
     * compatible - so setting/changing preferred_room_name fixes blocks that
     * were assigned before the preference existed or changed, not just future
     * ones. Leaves a block's room untouched if the type isn't compatible, same
     * as BlockGenerationService's own defaulting. Mirrors
     * TeacherController.backfillRequiredRoom for the group-level preference.
     */
    private void backfillPreferredRoom(StudentGroupEntity group) {
        String preferredRoomName = group.getPreferredRoomName();
        if (preferredRoomName == null) {
            return;
        }
        RoomEntity preferredRoom = roomRepository.findById(preferredRoomName).orElse(null);
        if (preferredRoom == null) {
            return;
        }
        for (CourseBlockAssignmentEntity block : assignmentRepository.findByGroupId(group.getId())) {
            if (Boolean.TRUE.equals(block.getPinned()) || preferredRoomName.equals(block.getRoomName())) {
                continue;
            }
            if (RoomTypeCompatibility.satisfies(preferredRoom.getType(), block.getSatisfiesRoomType())) {
                block.setRoomName(preferredRoomName);
                assignmentRepository.save(block);
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        StudentGroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
        // course_block_assignment.group_id is ON DELETE CASCADE, so deleting the
        // group would silently delete every schedule block for it too - block
        // instead, same guard shape as TimeslotController.
        long usageCount = assignmentRepository.countByGroupId(id);
        if (usageCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete: this group has " + usageCount + " schedule block(s). Delete those assignments first.");
        }
        groupRepository.delete(group);
        return ResponseEntity.noContent().build();
    }
}
