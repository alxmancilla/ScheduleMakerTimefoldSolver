package com.example.web.controller;

import com.example.web.dto.TeacherDTO;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.entity.TeacherQualificationEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.common.RoomTypeCompatibility;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final CourseBlockAssignmentRepository assignmentRepository;
    private final RoomRepository roomRepository;
    private final CourseRepository courseRepository;

    public TeacherController(TeacherRepository teacherRepository,
            CourseBlockAssignmentRepository assignmentRepository, RoomRepository roomRepository,
            CourseRepository courseRepository) {
        this.teacherRepository = teacherRepository;
        this.assignmentRepository = assignmentRepository;
        this.roomRepository = roomRepository;
        this.courseRepository = courseRepository;
    }

    @GetMapping
    public List<TeacherEntity> getAllTeachers() {
        return teacherRepository.findAll();
    }

    @GetMapping("/{id}")
    public TeacherEntity getTeacherById(@PathVariable String id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
    }

    @GetMapping("/search")
    public List<TeacherEntity> searchTeachers(@RequestParam String query) {
        return teacherRepository.findByNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    @PostMapping
    @Transactional
    public TeacherEntity createTeacher(
            @Validated({ Default.class, TeacherDTO.Create.class }) @RequestBody TeacherDTO request) {
        if (teacherRepository.existsById(request.getId())) {
            throw new IllegalArgumentException("Teacher with ID '" + request.getId() + "' already exists");
        }
        TeacherEntity teacher = new TeacherEntity(request.getId(), request.getName(), request.getLastName(),
                request.getMaxHoursPerWeek());
        teacher.setRequiredRoomName(request.getRequiredRoomName());
        applyQualifications(teacher, request.getQualifications());
        applyAvailability(teacher, request.getAvailability());
        return teacherRepository.save(teacher);
    }

    @PutMapping("/{id}")
    @Transactional
    public TeacherEntity updateTeacher(@PathVariable String id, @Valid @RequestBody TeacherDTO request) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
        teacher.setName(request.getName());
        teacher.setLastName(request.getLastName());
        teacher.setMaxHoursPerWeek(request.getMaxHoursPerWeek());
        teacher.setRequiredRoomName(request.getRequiredRoomName());
        applyQualifications(teacher, request.getQualifications());
        applyAvailability(teacher, request.getAvailability());
        TeacherEntity saved = teacherRepository.save(teacher);
        backfillRequiredRoom(saved);
        return saved;
    }

    /**
     * When a teacher has a required room, forces it onto every existing
     * (non-pinned) block already assigned to them whose room type is
     * compatible - so setting/changing required_room_name fixes blocks that
     * were assigned before the requirement existed, not just future ones.
     * Leaves a block's room untouched if the type isn't compatible (e.g. a
     * lab-required block for a teacher whose required room is Standard), same
     * as BlockGenerationService's own defaulting.
     */
    private void backfillRequiredRoom(TeacherEntity teacher) {
        String requiredRoomName = teacher.getRequiredRoomName();
        if (requiredRoomName == null) {
            return;
        }
        RoomEntity requiredRoom = roomRepository.findById(requiredRoomName).orElse(null);
        if (requiredRoom == null) {
            return;
        }
        for (CourseBlockAssignmentEntity block : assignmentRepository.findByTeacherId(teacher.getId())) {
            if (Boolean.TRUE.equals(block.getPinned()) || requiredRoomName.equals(block.getRoomName())) {
                continue;
            }
            if (RoomTypeCompatibility.satisfies(requiredRoom.getType(), block.getSatisfiesRoomType())) {
                block.setRoomName(requiredRoomName);
                assignmentRepository.save(block);
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeacher(@PathVariable String id) {
        TeacherEntity teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
        teacherRepository.delete(teacher);
        return ResponseEntity.noContent().build();
    }

    /**
     * Synchronizes the teacher's qualifications with the requested ones:
     * keeps existing matches, removes obsolete rows (orphanRemoval) and adds new
     * ones. The managed collection is mutated in place so Hibernate can track it.
     * A null request collection means "leave unchanged".
     *
     * Each qualification must name a real course (active or not - a teacher can
     * stay qualified for a currently-inactive course). teacherMustBeQualified
     * matches a qualification against course.getName() by exact string equality,
     * so an unvalidated typo would previously be accepted but silently never
     * match anything.
     */
    private void applyQualifications(TeacherEntity teacher, Set<String> requested) {
        if (requested == null) {
            return;
        }
        Set<String> desired = requested.stream()
                .filter(qualification -> qualification != null && !qualification.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());

        List<String> unknown = desired.stream()
                .filter(qualification -> !courseRepository.existsByName(qualification))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Qualification(s) do not match any existing course: " + String.join(", ", unknown));
        }

        teacher.getQualifications().removeIf(existing -> !desired.contains(existing.getQualification()));

        Set<String> current = teacher.getQualifications().stream()
                .map(TeacherQualificationEntity::getQualification)
                .collect(Collectors.toSet());

        desired.stream()
                .filter(qualification -> !current.contains(qualification))
                .forEach(teacher::addQualification);
    }

    /**
     * Synchronizes the teacher's availability slots with the requested ones,
     * identified by the (dayOfWeek, hour) pair.
     * A null request collection means "leave unchanged".
     */
    private void applyAvailability(TeacherEntity teacher, Set<TeacherDTO.AvailabilityDTO> requested) {
        if (requested == null) {
            return;
        }
        Set<String> desired = requested.stream()
                .filter(slot -> slot.getDayOfWeek() != null && slot.getHour() != null)
                .map(slot -> availabilityKey(slot.getDayOfWeek(), slot.getHour()))
                .collect(Collectors.toSet());

        teacher.getAvailability()
                .removeIf(existing -> !desired.contains(availabilityKey(existing.getDayOfWeek(), existing.getHour())));

        Set<String> current = teacher.getAvailability().stream()
                .map(slot -> availabilityKey(slot.getDayOfWeek(), slot.getHour()))
                .collect(Collectors.toSet());

        requested.stream()
                .filter(slot -> slot.getDayOfWeek() != null && slot.getHour() != null)
                .filter(slot -> current.add(availabilityKey(slot.getDayOfWeek(), slot.getHour())))
                .forEach(slot -> teacher.addAvailability(slot.getDayOfWeek(), slot.getHour()));
    }

    private String availabilityKey(Integer dayOfWeek, Integer hour) {
        return dayOfWeek + "-" + hour;
    }
}
