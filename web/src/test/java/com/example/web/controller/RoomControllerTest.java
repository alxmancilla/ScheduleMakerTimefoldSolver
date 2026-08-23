package com.example.web.controller;

import com.example.web.entity.RoomEntity;
import com.example.web.repository.RoomRepository;
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
 * Web-layer tests for {@link RoomController}, covering DTO validation and the
 * error responses produced by {@code GlobalExceptionHandler}. Uses the MVC
 * slice
 * with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(RoomController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoomRepository roomRepository;

    private RoomEntity room;

    @Before
    public void setUp() {
        room = new RoomEntity("A1", "Main", "estandar");
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "A1");
        body.put("building", "Main");
        body.put("type", "estandar");
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getAllRooms_returnsList() throws Exception {
        when(roomRepository.findAll()).thenReturn(List.of(room));
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("A1"));
    }

    @Test
    public void getRoomByName_found_returnsRoom() throws Exception {
        when(roomRepository.findById("A1")).thenReturn(Optional.of(room));
        mockMvc.perform(get("/api/rooms/A1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("estandar"));
    }

    @Test
    public void getRoomByName_notFound_returns404() throws Exception {
        when(roomRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/rooms/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Room with ID 'nope' not found"));
    }

    // ---- POST (create) ----

    @Test
    public void createRoom_valid_returnsSaved() throws Exception {
        when(roomRepository.existsById("A1")).thenReturn(false);
        when(roomRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A1"));
        verify(roomRepository).save(any(RoomEntity.class));
    }

    @Test
    public void createRoom_duplicateName_returns400() throws Exception {
        when(roomRepository.existsById("A1")).thenReturn(true);
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(roomRepository, never()).save(any(RoomEntity.class));
    }

    @Test
    public void createRoom_blankBuilding_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("building", "");
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.building").exists());
    }

    @Test
    public void createRoom_missingName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("name");
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    public void createRoom_missingType_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("type");
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    // ---- PUT (update) ----

    @Test
    public void updateRoom_valid_returnsUpdated() throws Exception {
        when(roomRepository.findById("A1")).thenReturn(Optional.of(room));
        when(roomRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.remove("name");
        body.put("type", "mixto");
        mockMvc.perform(put("/api/rooms/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("mixto"));
        verify(roomRepository).save(any(RoomEntity.class));
    }

    @Test
    public void updateRoom_notFound_returns404() throws Exception {
        when(roomRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = validPayload();
        body.remove("name");
        mockMvc.perform(put("/api/rooms/nope").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(roomRepository, never()).save(any(RoomEntity.class));
    }

    @Test
    public void updateRoom_blankType_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("name");
        body.put("type", "");
        mockMvc.perform(put("/api/rooms/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    // ---- DELETE ----

    @Test
    public void deleteRoom_existing_returns204() throws Exception {
        when(roomRepository.findById("A1")).thenReturn(Optional.of(room));
        mockMvc.perform(delete("/api/rooms/A1"))
                .andExpect(status().isNoContent());
        verify(roomRepository).delete(room);
    }

    @Test
    public void deleteRoom_notFound_returns404() throws Exception {
        when(roomRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/rooms/nope"))
                .andExpect(status().isNotFound());
        verify(roomRepository, never()).delete(any(RoomEntity.class));
    }
}
