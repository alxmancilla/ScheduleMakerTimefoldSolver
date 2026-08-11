package com.example.web.controller;

import com.example.web.entity.StudentGroupEntity;
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
 * Web-layer tests for {@link StudentGroupController}, covering DTO validation
 * and
 * the error responses produced by {@code GlobalExceptionHandler}. Uses the MVC
 * slice with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(StudentGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
public class StudentGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StudentGroupRepository groupRepository;

    private StudentGroupEntity group;

    @Before
    public void setUp() {
        group = new StudentGroupEntity("G1", "Group One");
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "G1");
        body.put("name", "Group One");
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getAllGroups_returnsList() throws Exception {
        when(groupRepository.findAll()).thenReturn(List.of(group));
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("G1"));
    }

    @Test
    public void getGroupById_found_returnsGroup() throws Exception {
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));
        mockMvc.perform(get("/api/groups/G1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Group One"));
    }

    @Test
    public void getGroupById_notFound_returns404() throws Exception {
        when(groupRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/groups/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Group with ID 'nope' not found"));
    }

    // ---- POST (create) ----

    @Test
    public void createGroup_valid_returnsSaved() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(false);
        when(groupRepository.save(any(StudentGroupEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("G1"));
        verify(groupRepository).save(any(StudentGroupEntity.class));
    }

    @Test
    public void createGroup_duplicateId_returns400() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(true);
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(groupRepository, never()).save(any(StudentGroupEntity.class));
    }

    @Test
    public void createGroup_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("name", "");
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    public void createGroup_invalidIdPattern_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("id", "bad id!");
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    public void createGroup_missingId_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("id");
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    // ---- PUT (update) ----

    @Test
    public void updateGroup_valid_returnsUpdated() throws Exception {
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));
        when(groupRepository.save(any(StudentGroupEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("name", "Group Renamed");
        mockMvc.perform(put("/api/groups/G1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Group Renamed"));
        verify(groupRepository).save(any(StudentGroupEntity.class));
    }

    @Test
    public void updateGroup_notFound_returns404() throws Exception {
        when(groupRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = validPayload();
        body.remove("id");
        mockMvc.perform(put("/api/groups/nope").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(groupRepository, never()).save(any(StudentGroupEntity.class));
    }

    @Test
    public void updateGroup_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("name", "");
        mockMvc.perform(put("/api/groups/G1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ---- DELETE ----

    @Test
    public void deleteGroup_existing_returns204() throws Exception {
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));
        mockMvc.perform(delete("/api/groups/G1"))
                .andExpect(status().isNoContent());
        verify(groupRepository).delete(group);
    }

    @Test
    public void deleteGroup_notFound_returns404() throws Exception {
        when(groupRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/groups/nope"))
                .andExpect(status().isNotFound());
        verify(groupRepository, never()).delete(any(StudentGroupEntity.class));
    }
}
