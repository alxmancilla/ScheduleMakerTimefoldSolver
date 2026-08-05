package com.example.web.controller;

import com.example.web.dto.StudentGroupDTO;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.StudentGroupRepository;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class StudentGroupController {

    @Autowired
    private StudentGroupRepository groupRepository;

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
        return groupRepository.save(group);
    }

    @PutMapping("/{id}")
    public StudentGroupEntity updateGroup(@PathVariable String id, @Valid @RequestBody StudentGroupDTO request) {
        StudentGroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
        group.setName(request.getName());
        group.setPreferredRoomName(request.getPreferredRoomName());
        return groupRepository.save(group);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        StudentGroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
        groupRepository.delete(group);
        return ResponseEntity.noContent().build();
    }
}
