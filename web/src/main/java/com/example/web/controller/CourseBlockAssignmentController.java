package com.example.web.controller;

import com.example.web.dto.AssignmentImportResponse;
import com.example.web.dto.AssignmentMoveValidationRequest;
import com.example.web.dto.AssignmentMoveValidationResponse;
import com.example.web.dto.CourseBlockAssignmentDTO;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.web.service.AssignmentExcelService;
import com.example.web.service.AssignmentMoveValidationService;
import com.example.web.service.GroupCourseDefaultTeacherSyncService;
import com.example.common.RoomTypeCompatibility;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Autowired
    private AssignmentExcelService assignmentExcelService;

    @Autowired
    private GroupCourseDefaultTeacherSyncService groupCourseDefaultTeacherSyncService;

    @Autowired
    private AssignmentMoveValidationService assignmentMoveValidationService;

    @GetMapping
    public List<CourseBlockAssignmentEntity> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    @GetMapping("/{id}")
    public CourseBlockAssignmentEntity getAssignmentById(@PathVariable String id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }

    @PostMapping("/{id}/validate-move")
    public AssignmentMoveValidationResponse validateMove(@PathVariable String id,
            @Valid @RequestBody AssignmentMoveValidationRequest request) {
        return assignmentMoveValidationService.validate(id, request.getBlockTimeslotId(), request.isPinned());
    }

    /**
     * Move/pin a block without touching anything else about it - the save
     * path behind the Schedule grid's move/pin editor. Deliberately a
     * narrower endpoint than the general PUT below (which carries the full
     * DTO and can change room/teacher/course too): this one only ever sets
     * blockTimeslotId/pinned, same shape as AssignmentMoveValidationRequest.
     * Both this and the general PUT require SCHEDULER or ADMIN (see
     * SecurityConfig) - WRITER does not get either, unlike its usual write
     * access to every other domain-data resource. Re-validates server-side
     * before saving - the frontend already blocks Save on a violation, but
     * a client-side check is advisory only from the server's point of view,
     * and a violation reported as a warning (a currently-SOFT-configured
     * constraint) must still not become a HARD one here if the config
     * changed between the client's last check and this request.
     */
    @PutMapping("/{id}/move")
    public CourseBlockAssignmentEntity moveAssignment(@PathVariable String id,
            @Valid @RequestBody AssignmentMoveValidationRequest request) {
        AssignmentMoveValidationResponse validation = assignmentMoveValidationService.validate(id,
                request.getBlockTimeslotId(), request.isPinned());
        if (!validation.getViolations().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot save: " + String.join("; ", validation.getViolations()));
        }
        CourseBlockAssignmentEntity assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        boolean wasPinned = Boolean.TRUE.equals(assignment.getPinned());

        // A move that doesn't pin has nowhere to be recorded (added 2026-09-08).
        // course_block_assignment_current takes an unpinned row's placement from
        // the latest schedule_run and ignores block_timeslot_id entirely, so
        // writing a new slot on an unpinned row changed nothing a user could
        // see: the editor reported success and the grid did not move. Rather
        // than persist data nothing reads, reject the combination outright.
        // Unpinning a block where it already sits stays legal - that's a
        // release, not a move, and stampPinProvenance clears the now-meaningless
        // timeslot along with the provenance fields.
        boolean slotChanged = !request.getBlockTimeslotId().equals(assignment.getBlockTimeslotId());
        if (!request.isPinned() && slotChanged && wasPinned) {
            throw new IllegalArgumentException(
                    "Cannot move a block without pinning it: an unpinned block takes its position from the "
                            + "latest solver run, so the new slot would be ignored. Pin it to place it here, "
                            + "or unpin it where it currently sits to hand it back to the solver.");
        }
        if (!request.isPinned() && !wasPinned) {
            throw new IllegalArgumentException(
                    "This block is not pinned, so its position is decided by the solver and cannot be set "
                            + "manually. Pin it to place it explicitly.");
        }
        assignment.setBlockTimeslotId(request.getBlockTimeslotId());
        assignment.setPinned(request.isPinned());
        stampPinProvenance(assignment, wasPinned, currentUsername());
        return assignmentRepository.save(assignment);
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
        applyFields(assignment, request, false, currentUsername());
        CourseBlockAssignmentEntity saved = assignmentRepository.save(assignment);
        groupCourseDefaultTeacherSyncService.sync(saved.getGroupId(), saved.getCourseId(), saved.getTeacherId());
        return saved;
    }

    @PutMapping("/{id}")
    public CourseBlockAssignmentEntity updateAssignment(@PathVariable String id,
            @Valid @RequestBody CourseBlockAssignmentDTO request) {
        CourseBlockAssignmentEntity assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        boolean wasPinned = Boolean.TRUE.equals(assignment.getPinned());
        applyFields(assignment, request, wasPinned, currentUsername());
        CourseBlockAssignmentEntity saved = assignmentRepository.save(assignment);
        groupCourseDefaultTeacherSyncService.sync(saved.getGroupId(), saved.getCourseId(), saved.getTeacherId());
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable String id) {
        CourseBlockAssignmentEntity assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        assignmentRepository.delete(assignment);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(CourseBlockAssignmentEntity assignment, CourseBlockAssignmentDTO request,
            boolean wasPinned, String username) {
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
        if (Boolean.TRUE.equals(assignment.getPinned()) && assignment.getRoomName() == null) {
            throw new IllegalArgumentException(
                    "Cannot pin assignment '" + assignment.getId() + "' without a room: pinned blocks must have roomName set.");
        }
        stampPinProvenance(assignment, wasPinned, username);
    }

    /**
     * Stamps pinnedAt/pinnedBy/pinSource=USER whenever `pinned` flips
     * false->true through a user-facing write (create, the general update,
     * or move - see the three call sites), and clears all three back to
     * null when it flips true->false, since they stop meaning anything once
     * unpinned. Left untouched when `pinned` doesn't change across the edit
     * (e.g. changing room while already pinned shouldn't reset "when was
     * this pinned"). The one pin source this deliberately never covers is
     * SYSTEM - BlockGenerationService.tryPinExclusiveTeacherBlocks() saves
     * directly through the repository, never through this controller, and
     * stamps its own provenance the same way, with no username.
     */
    /**
     * The authenticated caller's username, for pin provenance (pinnedBy).
     * Read from SecurityContextHolder directly rather than via an injected
     * Authentication method parameter - Spring MVC's built-in resolver for a
     * plain Authentication parameter goes through
     * HttpServletRequest.getUserPrincipal(), which needs the security filter
     * chain to have actually run to be populated. Reading
     * SecurityContextHolder directly works identically in production
     * (SecurityConfig's own chain populates it before any controller runs)
     * and in a @WebMvcTest slice that stubs authentication via
     * @WithMockUser without running the filter chain at all (confirmed
     * live - the parameter-injection approach 500'd with a NullPointerException
     * in exactly that slice, since CourseBlockAssignmentControllerTest
     * deliberately disables filters to focus on business-logic assertions).
     */
    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private void stampPinProvenance(CourseBlockAssignmentEntity assignment, boolean wasPinned, String username) {
        boolean isPinned = Boolean.TRUE.equals(assignment.getPinned());
        if (!wasPinned && isPinned) {
            assignment.setPinnedAt(LocalDateTime.now());
            assignment.setPinnedBy(username);
            assignment.setPinSource("USER");
        } else if (wasPinned && !isPinned) {
            assignment.setPinnedAt(null);
            assignment.setPinnedBy(null);
            assignment.setPinSource(null);
            // The timeslot goes with them (added 2026-09-08). block_timeslot_id
            // is only ever meaningful for a pinned row - course_block_assignment
            // is pure solver input, and course_block_assignment_current resolves
            // an unpinned row to the latest run's placement, ignoring this column
            // entirely. Leaving it set on unpin left dead data that misrepresents
            // the row: found exactly one such row in production (1A-ARH_1001_0,
            // still holding block_87 from a pin/unpin round-trip a day earlier),
            // and it only came to light because regenerating the block cleared
            // it. Cleared here, alongside provenance and on the same true->false
            // transition, so "has a timeslot" keeps matching "is pinned".
            assignment.setBlockTimeslotId(null);
        }
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

    // ---- Excel export/import (whole table, SCHEDULER/ADMIN-only same as
    // everything else under /api/assignments/**) - see AssignmentExcelService's
    // own doc for why this is separate from ImportController's base-data flow. ----

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] workbook = assignmentExcelService.exportToExcel();
        String filename = "assignments-export-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(workbook);
    }

    @PostMapping("/import")
    public ResponseEntity<AssignmentImportResponse> importExcel(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        AssignmentExcelService.ImportResult result = assignmentExcelService.importFromExcel(file.getInputStream());
        AssignmentImportResponse response = new AssignmentImportResponse(result);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
}
