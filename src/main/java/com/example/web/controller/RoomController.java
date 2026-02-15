package com.example.web.controller;

import com.example.web.entity.RoomEntity;
import com.example.web.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    
    @Autowired
    private RoomRepository roomRepository;
    
    @GetMapping
    public List<RoomEntity> getAllRooms() {
        return roomRepository.findAll();
    }
    
    @GetMapping("/{name}")
    public ResponseEntity<RoomEntity> getRoomByName(@PathVariable String name) {
        return roomRepository.findById(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/type/{type}")
    public List<RoomEntity> getRoomsByType(@PathVariable String type) {
        return roomRepository.findByType(type);
    }
    
    @GetMapping("/building/{building}")
    public List<RoomEntity> getRoomsByBuilding(@PathVariable String building) {
        return roomRepository.findByBuilding(building);
    }
    
    @PostMapping
    public RoomEntity createRoom(@RequestBody RoomEntity room) {
        return roomRepository.save(room);
    }
    
    @PutMapping("/{name}")
    public ResponseEntity<RoomEntity> updateRoom(@PathVariable String name, @RequestBody RoomEntity roomDetails) {
        return roomRepository.findById(name)
                .map(room -> {
                    room.setBuilding(roomDetails.getBuilding());
                    room.setType(roomDetails.getType());
                    return ResponseEntity.ok(roomRepository.save(room));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String name) {
        return roomRepository.findById(name)
                .map(room -> {
                    roomRepository.delete(room);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

