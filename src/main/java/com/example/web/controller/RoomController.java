package com.example.web.controller;

import com.example.web.dto.RoomDTO;
import com.example.web.entity.RoomEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.RoomRepository;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
    public RoomEntity getRoomByName(@PathVariable String name) {
        return roomRepository.findById(name)
                .orElseThrow(() -> new ResourceNotFoundException("Room", name));
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
    public RoomEntity createRoom(
            @Validated({ Default.class, RoomDTO.Create.class }) @RequestBody RoomDTO request) {
        if (roomRepository.existsById(request.getName())) {
            throw new IllegalArgumentException("Room with name '" + request.getName() + "' already exists");
        }
        RoomEntity room = new RoomEntity(request.getName(), request.getBuilding(), request.getType());
        return roomRepository.save(room);
    }

    @PutMapping("/{name}")
    public RoomEntity updateRoom(@PathVariable String name, @Valid @RequestBody RoomDTO request) {
        RoomEntity room = roomRepository.findById(name)
                .orElseThrow(() -> new ResourceNotFoundException("Room", name));
        room.setBuilding(request.getBuilding());
        room.setType(request.getType());
        return roomRepository.save(room);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String name) {
        RoomEntity room = roomRepository.findById(name)
                .orElseThrow(() -> new ResourceNotFoundException("Room", name));
        roomRepository.delete(room);
        return ResponseEntity.noContent().build();
    }
}
