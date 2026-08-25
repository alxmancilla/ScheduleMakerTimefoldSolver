package com.example.web.controller;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.GroupRoomRangeEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.GroupRoomRangeRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link GroupRoomRangeController}: CRUD for a group's
 * curated acceptable-room ranges, nested under
 * /api/groups/{groupId}/room-ranges, plus the backfill-onto-existing-blocks
 * behavior (mirrors the old StudentGroupController.backfillPreferredRoom,
 * now triggered here instead since ranges are edited through this
 * controller). Uses the MVC slice with mocked repositories so no database is
 * required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(GroupRoomRangeController.class)
@AutoConfigureMockMvc(addFilters = false)
public class GroupRoomRangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupRoomRangeRepository rangeRepository;

    @MockBean
    private StudentGroupRepository groupRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    private GroupRoomRangeEntity range;

    @Before
    public void setUp() {
        range = new GroupRoomRangeEntity("G1", "estándar", "ROOM1");
        range.setId(10L);
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(assignmentRepository.findByGroupId("G1")).thenReturn(List.of());
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomType", "estándar");
        body.put("roomName", "ROOM1");
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private CourseBlockAssignmentEntity block(String id, String satisfiesRoomType, String roomName, boolean pinned) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(id);
        block.setGroupId("G1");
        block.setCourseId("C1");
        block.setBlockLength(2);
        block.setSatisfiesRoomType(satisfiesRoomType);
        block.setRoomName(roomName);
        block.setPinned(pinned);
        return block;
    }

    // ---- GET ----

    @Test
    public void getRanges_groupExists_returnsList() throws Exception {
        when(rangeRepository.findByGroupIdOrderByRoomType("G1")).thenReturn(List.of(range));
        mockMvc.perform(get("/api/groups/G1/room-ranges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomType").value("estándar"))
                .andExpect(jsonPath("$[0].roomName").value("ROOM1"));
    }

    @Test
    public void getRanges_groupNotFound_returns404() throws Exception {
        when(groupRepository.existsById("nope")).thenReturn(false);
        mockMvc.perform(get("/api/groups/nope/room-ranges"))
                .andExpect(status().isNotFound());
    }

    // ---- POST (create) ----

    @Test
    public void createRange_valid_returnsSaved() throws Exception {
        when(roomRepository.existsById("ROOM1")).thenReturn(true);
        when(rangeRepository.save(any(GroupRoomRangeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rangeRepository.findByGroupIdAndRoomType("G1", "estándar")).thenReturn(List.of(range));
        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomType").value("estándar"))
                .andExpect(jsonPath("$.roomName").value("ROOM1"));
        verify(rangeRepository).save(any(GroupRoomRangeEntity.class));
    }

    @Test
    public void createRange_groupNotFound_returns404() throws Exception {
        when(groupRepository.existsById("nope")).thenReturn(false);
        mockMvc.perform(post("/api/groups/nope/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(rangeRepository, never()).save(any(GroupRoomRangeEntity.class));
    }

    @Test
    public void createRange_blankRoomType_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("roomType", "");
        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.roomType").exists());
    }

    @Test
    public void createRange_unknownRoom_returns400() throws Exception {
        when(roomRepository.existsById("ROOM1")).thenReturn(false);
        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
        verify(rangeRepository, never()).save(any(GroupRoomRangeEntity.class));
    }

    // ---- PUT (update) ----

    @Test
    public void updateRange_valid_returnsUpdated() throws Exception {
        when(rangeRepository.findById(10L)).thenReturn(Optional.of(range));
        when(roomRepository.existsById("ROOM2")).thenReturn(true);
        when(rangeRepository.save(any(GroupRoomRangeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rangeRepository.findByGroupIdAndRoomType(any(), any())).thenReturn(List.of());
        Map<String, Object> body = validPayload();
        body.put("roomName", "ROOM2");
        mockMvc.perform(put("/api/groups/G1/room-ranges/10")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("ROOM2"));
    }

    @Test
    public void updateRange_notFound_returns404() throws Exception {
        when(rangeRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/groups/G1/room-ranges/99")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(rangeRepository, never()).save(any(GroupRoomRangeEntity.class));
    }

    @Test
    public void updateRange_belongsToDifferentGroup_returns404() throws Exception {
        GroupRoomRangeEntity otherGroupsRange = new GroupRoomRangeEntity("G2", "estándar", "ROOM1");
        otherGroupsRange.setId(10L);
        when(rangeRepository.findById(10L)).thenReturn(Optional.of(otherGroupsRange));
        mockMvc.perform(put("/api/groups/G1/room-ranges/10")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(rangeRepository, never()).save(any(GroupRoomRangeEntity.class));
    }

    // ---- DELETE ----

    @Test
    public void deleteRange_existing_returns204() throws Exception {
        when(rangeRepository.findById(10L)).thenReturn(Optional.of(range));
        when(rangeRepository.findByGroupIdAndRoomType("G1", "estándar")).thenReturn(List.of());
        mockMvc.perform(delete("/api/groups/G1/room-ranges/10"))
                .andExpect(status().isNoContent());
        verify(rangeRepository).delete(range);
    }

    @Test
    public void deleteRange_notFound_returns404() throws Exception {
        when(rangeRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/groups/G1/room-ranges/99"))
                .andExpect(status().isNotFound());
        verify(rangeRepository, never()).delete(any(GroupRoomRangeEntity.class));
    }

    // ---- Backfill onto existing blocks ----

    @Test
    public void createRange_resolvesToSingleton_backfillsCompatibleExistingBlock() throws Exception {
        when(roomRepository.existsById("ROOM1")).thenReturn(true);
        when(roomRepository.findById("ROOM1"))
                .thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        when(rangeRepository.save(any(GroupRoomRangeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rangeRepository.findByGroupIdAndRoomType("G1", "estándar")).thenReturn(List.of(range));
        CourseBlockAssignmentEntity existing = block("A1", "estándar", null, false);
        when(assignmentRepository.findByGroupId("G1")).thenReturn(List.of(existing));

        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk());

        verify(assignmentRepository).save(existing);
        org.junit.Assert.assertEquals("ROOM1", existing.getRoomName());
    }

    @Test
    public void createRange_resolvesToSingleton_skipsIncompatibleBlock() throws Exception {
        // A mixto-required block can't be forced into a plain estándar room.
        when(roomRepository.existsById("ROOM1")).thenReturn(true);
        when(roomRepository.findById("ROOM1"))
                .thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        when(rangeRepository.save(any(GroupRoomRangeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rangeRepository.findByGroupIdAndRoomType("G1", "estándar")).thenReturn(List.of(range));
        CourseBlockAssignmentEntity existing = block("A1", "mixto", null, false);
        when(assignmentRepository.findByGroupId("G1")).thenReturn(List.of(existing));

        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk());

        verify(assignmentRepository, never()).save(existing);
    }

    @Test
    public void createRange_resolvesToSingleton_skipsPinnedBlock() throws Exception {
        when(roomRepository.existsById("ROOM1")).thenReturn(true);
        when(roomRepository.findById("ROOM1"))
                .thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        when(rangeRepository.save(any(GroupRoomRangeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rangeRepository.findByGroupIdAndRoomType("G1", "estándar")).thenReturn(List.of(range));
        CourseBlockAssignmentEntity pinned = block("A1", "estándar", "ROOM2", true);
        when(assignmentRepository.findByGroupId("G1")).thenReturn(List.of(pinned));

        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk());

        verify(assignmentRepository, never()).save(pinned);
    }

    @Test
    public void createRange_stillTwoOrMoreRooms_doesNotBackfill_noSingleDeterministicChoice() throws Exception {
        when(roomRepository.existsById("ROOM1")).thenReturn(true);
        when(rangeRepository.save(any(GroupRoomRangeEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        GroupRoomRangeEntity otherRoomInSameRange = new GroupRoomRangeEntity("G1", "estándar", "ROOM2");
        when(rangeRepository.findByGroupIdAndRoomType("G1", "estándar"))
                .thenReturn(List.of(range, otherRoomInSameRange));
        CourseBlockAssignmentEntity existing = block("A1", "estándar", null, false);
        when(assignmentRepository.findByGroupId("G1")).thenReturn(List.of(existing));

        mockMvc.perform(post("/api/groups/G1/room-ranges")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk());

        verify(assignmentRepository, never()).save(existing);
    }
}
