package com.example.web.controller;

import com.example.web.dto.RoomDTO;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.RoomUtilizationEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.RoomUtilizationRepository;
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

    @Autowired
    private RoomUtilizationRepository roomUtilizationRepository;

    @GetMapping
    public List<RoomEntity> getAllRooms() {
        return roomRepository.findAll();
    }

    /**
     * Backed by v_room_utilization (assignments count, distinct timeslots
     * used, and total hours booked per week, computed server-side). Its own
     * endpoint rather than requiring /api/assignments/** access - that path
     * is ADMIN-only, but this utilization summary is shown to any role that
     * can view the Rooms page.
     */
    @GetMapping("/utilization")
    public List<RoomUtilizationEntity> getUtilization() {
        return roomUtilizationRepository.findAll();
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
        room.setCapacity(request.getCapacity());
        return roomRepository.save(room);
    }

    @PutMapping("/{name}")
    public RoomEntity updateRoom(@PathVariable String name, @Valid @RequestBody RoomDTO request) {
        RoomEntity room = roomRepository.findById(name)
                .orElseThrow(() -> new ResourceNotFoundException("Room", name));
        room.setBuilding(request.getBuilding());
        room.setType(request.getType());
        room.setCapacity(request.getCapacity());
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
