package com.example.web.controller;

import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.StudentGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<StudentGroupEntity> getGroupById(@PathVariable String id) {
        return groupRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/search")
    public List<StudentGroupEntity> searchGroups(@RequestParam String query) {
        return groupRepository.findByNameContainingIgnoreCase(query);
    }
    
    @PostMapping
    public StudentGroupEntity createGroup(@RequestBody StudentGroupEntity group) {
        return groupRepository.save(group);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<StudentGroupEntity> updateGroup(@PathVariable String id, @RequestBody StudentGroupEntity groupDetails) {
        return groupRepository.findById(id)
                .map(group -> {
                    group.setName(groupDetails.getName());
                    group.setPreferredRoomName(groupDetails.getPreferredRoomName());
                    return ResponseEntity.ok(groupRepository.save(group));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        return groupRepository.findById(id)
                .map(group -> {
                    groupRepository.delete(group);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

