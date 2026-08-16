package com.example.web.controller;

import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
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
 * Web-layer tests for {@link CourseBlockTemplateController}: CRUD for a
 * course's custom block templates, nested under /api/courses/{courseId}/block-templates.
 * Uses the MVC slice with mocked repositories so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CourseBlockTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CourseBlockTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseBlockTemplateRepository templateRepository;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private StudentGroupRepository groupRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private BlockTimeslotRepository timeslotRepository;

    private CourseBlockTemplateEntity template;

    @Before
    public void setUp() {
        template = new CourseBlockTemplateEntity("C1", "G1", 0, 2, "centro de cómputo", "CC 1", 1, false, null);
        template.setId(10L);
        when(courseRepository.existsById("C1")).thenReturn(true);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("groupId", "G1");
        body.put("blockIndex", 0);
        body.put("blockLength", 2);
        body.put("roomType", "centro de cómputo");
        body.put("preferredRoomName", "CC 1");
        body.put("preferredDay", 1);
        body.put("pinAssignment", false);
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getTemplates_courseExists_returnsList() throws Exception {
        when(templateRepository.findByCourseIdOrderByGroupIdAscBlockIndexAsc("C1")).thenReturn(List.of(template));
        mockMvc.perform(get("/api/courses/C1/block-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].blockIndex").value(0));
    }

    @Test
    public void getTemplates_courseNotFound_returns404() throws Exception {
        when(courseRepository.existsById("nope")).thenReturn(false);
        mockMvc.perform(get("/api/courses/nope/block-templates"))
                .andExpect(status().isNotFound());
    }

    // ---- POST (create) ----

    @Test
    public void createTemplate_valid_returnsSaved() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(templateRepository.save(any(CourseBlockTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockLength").value(2))
                .andExpect(jsonPath("$.roomType").value("centro de cómputo"));
        verify(templateRepository).save(any(CourseBlockTemplateEntity.class));
    }

    @Test
    public void createTemplate_courseNotFound_returns404() throws Exception {
        when(courseRepository.existsById("nope")).thenReturn(false);
        mockMvc.perform(post("/api/courses/nope/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(templateRepository, never()).save(any(CourseBlockTemplateEntity.class));
    }

    @Test
    public void createTemplate_noGroupId_appliesEverywhere() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("groupId");
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(templateRepository.save(any(CourseBlockTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").doesNotExist());
        verify(groupRepository, never()).existsById(any());
    }

    @Test
    public void createTemplate_unknownGroup_returns400() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(false);
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
        verify(templateRepository, never()).save(any(CourseBlockTemplateEntity.class));
    }

    @Test
    public void createTemplate_unknownPreferredRoom_returns400() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(roomRepository.existsById("CC 1")).thenReturn(false);
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
    }

    @Test
    public void createTemplate_unknownTimeslot_returns400() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(timeslotRepository.existsById("block_bad")).thenReturn(false);
        Map<String, Object> body = validPayload();
        body.put("preferredTimeslotId", "block_bad");
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
    }

    @Test
    public void createTemplate_duplicateGroupAndIndex_returns400() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(templateRepository.existsByCourseIdAndGroupIdAndBlockIndex("C1", "G1", 0)).thenReturn(true);
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(templateRepository, never()).save(any(CourseBlockTemplateEntity.class));
    }

    @Test
    public void createTemplate_pinnedWithoutTimeslot_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("pinAssignment", true);
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pinnedRequiresTimeslot").exists());
    }

    @Test
    public void createTemplate_pinnedWithTimeslot_succeeds() throws Exception {
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(timeslotRepository.existsById("block_abc")).thenReturn(true);
        when(templateRepository.save(any(CourseBlockTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.put("pinAssignment", true);
        body.put("preferredTimeslotId", "block_abc");
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinAssignment").value(true));
    }

    @Test
    public void createTemplate_blockLengthOutOfRange_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("blockLength", 5);
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.blockLength").exists());
    }

    @Test
    public void createTemplate_missingBlockIndex_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("blockIndex");
        mockMvc.perform(post("/api/courses/C1/block-templates")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.blockIndex").exists());
    }

    // ---- PUT (update) ----

    @Test
    public void updateTemplate_valid_returnsUpdated() throws Exception {
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(groupRepository.existsById("G1")).thenReturn(true);
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(templateRepository.save(any(CourseBlockTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.put("blockLength", 3);
        mockMvc.perform(put("/api/courses/C1/block-templates/10")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockLength").value(3));
    }

    @Test
    public void updateTemplate_notFound_returns404() throws Exception {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/courses/C1/block-templates/99")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(templateRepository, never()).save(any(CourseBlockTemplateEntity.class));
    }

    @Test
    public void updateTemplate_belongsToDifferentCourse_returns404() throws Exception {
        CourseBlockTemplateEntity otherCoursesTemplate = new CourseBlockTemplateEntity(
                "C2", null, 0, 1, "estándar", null, null, false, null);
        otherCoursesTemplate.setId(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(otherCoursesTemplate));
        mockMvc.perform(put("/api/courses/C1/block-templates/10")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(templateRepository, never()).save(any(CourseBlockTemplateEntity.class));
    }

    // ---- DELETE ----

    @Test
    public void deleteTemplate_existing_returns204() throws Exception {
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        mockMvc.perform(delete("/api/courses/C1/block-templates/10"))
                .andExpect(status().isNoContent());
        verify(templateRepository).delete(template);
    }

    @Test
    public void deleteTemplate_notFound_returns404() throws Exception {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/courses/C1/block-templates/99"))
                .andExpect(status().isNotFound());
        verify(templateRepository, never()).delete(any(CourseBlockTemplateEntity.class));
    }
}
