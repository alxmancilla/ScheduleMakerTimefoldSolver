package com.example.web.controller;

import com.example.common.RoomTypeCompatibility;
import com.example.web.dto.GroupRoomRangeDTO;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.GroupRoomRangeEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.GroupRoomRangeRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for a group's curated acceptable-room ranges (group_room_range): a
 * room type with no rows for a group is unrestricted for that group (falls
 * through to the solver's full type-filtered room list); one row is
 * structurally fixed to that single room, same as the old single
 * preferred_room_name; 2+ rows is a narrowed but still movable set. Nested
 * under /api/groups/{groupId}/room-ranges since a range row only ever makes
 * sense in the context of its group; falls under the general /api/**
 * GET/POST/PUT/DELETE role rules like the rest of /api/groups/**, no
 * ADMIN-only restriction.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/room-ranges")
public class GroupRoomRangeController {

    @Autowired
    private GroupRoomRangeRepository rangeRepository;

    @Autowired
    private StudentGroupRepository groupRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @GetMapping
    public List<GroupRoomRangeEntity> getRanges(@PathVariable String groupId) {
        requireGroup(groupId);
        return rangeRepository.findByGroupIdOrderByRoomType(groupId);
    }

    @PostMapping
    public GroupRoomRangeEntity createRange(@PathVariable String groupId,
            @Valid @RequestBody GroupRoomRangeDTO request) {
        requireGroup(groupId);
        validateRoom(request.getRoomName());
        GroupRoomRangeEntity range = new GroupRoomRangeEntity(groupId, request.getRoomType(), request.getRoomName());
        GroupRoomRangeEntity saved = rangeRepository.save(range);
        backfillRange(groupId, request.getRoomType());
        return saved;
    }

    @PutMapping("/{id}")
    public GroupRoomRangeEntity updateRange(@PathVariable String groupId, @PathVariable Long id,
            @Valid @RequestBody GroupRoomRangeDTO request) {
        GroupRoomRangeEntity range = requireRange(groupId, id);
        String previousRoomType = range.getRoomType();
        validateRoom(request.getRoomName());
        range.setRoomType(request.getRoomType());
        range.setRoomName(request.getRoomName());
        GroupRoomRangeEntity saved = rangeRepository.save(range);
        backfillRange(groupId, previousRoomType);
        if (!previousRoomType.equals(request.getRoomType())) {
            backfillRange(groupId, request.getRoomType());
        }
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRange(@PathVariable String groupId, @PathVariable Long id) {
        GroupRoomRangeEntity range = requireRange(groupId, id);
        String roomType = range.getRoomType();
        rangeRepository.delete(range);
        backfillRange(groupId, roomType);
        return ResponseEntity.noContent().build();
    }

    /**
     * When a group's curated range for a room type resolves to exactly one
     * room, forces it onto every existing (non-pinned) block already
     * assigned to that group whose satisfiesRoomType matches - so
     * adding/editing/removing a range row fixes blocks that were assigned
     * before the range existed or changed, not just future ones. A range of
     * 2+ rooms has no single deterministic choice to force, so those blocks
     * are left for the next solve to decide among the (now on-disk) narrowed
     * range instead. Mirrors TeacherController.backfillRequiredRoom and the
     * old StudentGroupController.backfillPreferredRoom for the group-level,
     * per-room-type range.
     */
    private void backfillRange(String groupId, String roomType) {
        List<GroupRoomRangeEntity> range = rangeRepository.findByGroupIdAndRoomType(groupId, roomType);
        if (range.size() != 1) {
            return;
        }
        String onlyRoomName = range.get(0).getRoomName();
        RoomEntity onlyRoom = roomRepository.findById(onlyRoomName).orElse(null);
        if (onlyRoom == null) {
            return;
        }
        for (CourseBlockAssignmentEntity block : assignmentRepository.findByGroupId(groupId)) {
            if (Boolean.TRUE.equals(block.getPinned()) || onlyRoomName.equals(block.getRoomName())) {
                continue;
            }
            if (!roomType.equals(block.getSatisfiesRoomType())) {
                continue;
            }
            if (RoomTypeCompatibility.satisfies(onlyRoom.getType(), block.getSatisfiesRoomType())) {
                block.setRoomName(onlyRoomName);
                assignmentRepository.save(block);
            }
        }
    }

    private void requireGroup(String groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
    }

    private GroupRoomRangeEntity requireRange(String groupId, Long id) {
        requireGroup(groupId);
        GroupRoomRangeEntity range = rangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room range", String.valueOf(id)));
        if (!range.getGroupId().equals(groupId)) {
            throw new ResourceNotFoundException("Room range", String.valueOf(id));
        }
        return range;
    }

    private void validateRoom(String roomName) {
        if (!roomRepository.existsById(roomName)) {
            throw new IllegalArgumentException("Room '" + roomName + "' does not exist");
        }
    }
}
