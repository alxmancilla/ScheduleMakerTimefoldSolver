package com.example.web.controller;

import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
 * Web-layer tests for {@link GroupCourseController}: which courses a group
 * takes, nested under /api/groups/{groupId}/courses. Uses the MVC slice with
 * mocked repositories so no database is required; StudentGroupEntity's
 * courses Set is mutated in-memory the same way the real JPA cascade would.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(GroupCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
public class GroupCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StudentGroupRepository groupRepository;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private TeacherRepository teacherRepository;

    private StudentGroupEntity group;

    @Before
    public void setUp() {
        group = new StudentGroupEntity("G1", "Group One");
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));
        when(groupRepository.save(org.mockito.ArgumentMatchers.any(StudentGroupEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getCourses_groupExists_returnsList() throws Exception {
        group.addCourse("Mathematics");
        group.addCourse("Physics");
        mockMvc.perform(get("/api/groups/G1/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].courseName").value("Mathematics"))
                .andExpect(jsonPath("$[1].courseName").value("Physics"));
    }

    @Test
    public void getCourses_groupNotFound_returns404() throws Exception {
        when(groupRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/groups/nope/courses"))
                .andExpect(status().isNotFound());
    }

    // ---- POST (add) ----

    @Test
    public void addCourse_valid_returnsSaved() throws Exception {
        when(courseRepository.existsByName("Mathematics")).thenReturn(true);
        Map<String, Object> body = Map.of("courseName", "Mathematics");
        mockMvc.perform(post("/api/groups/G1/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value("G1"))
                .andExpect(jsonPath("$.courseName").value("Mathematics"));
        verify(groupRepository).save(group);
    }

    @Test
    public void addCourse_groupNotFound_returns404() throws Exception {
        when(groupRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = Map.of("courseName", "Mathematics");
        mockMvc.perform(post("/api/groups/nope/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
        verify(groupRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void addCourse_unknownCourse_returns400() throws Exception {
        when(courseRepository.existsByName("Nonexistent")).thenReturn(false);
        Map<String, Object> body = Map.of("courseName", "Nonexistent");
        mockMvc.perform(post("/api/groups/G1/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
        verify(groupRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void addCourse_alreadyLinked_returns400() throws Exception {
        group.addCourse("Mathematics");
        when(courseRepository.existsByName("Mathematics")).thenReturn(true);
        Map<String, Object> body = Map.of("courseName", "Mathematics");
        mockMvc.perform(post("/api/groups/G1/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already has course")));
        verify(groupRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void addCourse_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("courseName", "");
        mockMvc.perform(post("/api/groups/G1/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courseName").exists());
    }

    // ---- DELETE (remove) ----

    @Test
    public void removeCourse_existing_returns204() throws Exception {
        group.addCourse("Mathematics");
        mockMvc.perform(delete("/api/groups/G1/courses/Mathematics"))
                .andExpect(status().isNoContent());
        verify(groupRepository).save(group);
    }

    @Test
    public void removeCourse_notLinked_returns404() throws Exception {
        mockMvc.perform(delete("/api/groups/G1/courses/Mathematics"))
                .andExpect(status().isNotFound());
        verify(groupRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void removeCourse_groupNotFound_returns404() throws Exception {
        when(groupRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/groups/nope/courses/Mathematics"))
                .andExpect(status().isNotFound());
    }

    // ---- PUT (default teacher) ----

    @Test
    public void setDefaultTeacher_qualifiedTeacher_savesIt() throws Exception {
        group.addCourse("Mathematics");
        TeacherEntity teacher = new TeacherEntity("T1", "Ana", "Lopez", 40);
        teacher.addQualification("Mathematics");
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        Map<String, Object> body = Map.of("teacherId", "T1");
        mockMvc.perform(put("/api/groups/G1/courses/Mathematics/default-teacher")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTeacherId").value("T1"));
        verify(groupRepository).save(group);
    }

    @Test
    public void setDefaultTeacher_unqualifiedTeacher_returns400() throws Exception {
        group.addCourse("Mathematics");
        TeacherEntity teacher = new TeacherEntity("T1", "Ana", "Lopez", 40);
        teacher.addQualification("Physics");
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        Map<String, Object> body = Map.of("teacherId", "T1");
        mockMvc.perform(put("/api/groups/G1/courses/Mathematics/default-teacher")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not qualified")));
        verify(groupRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void setDefaultTeacher_unknownTeacher_returns400() throws Exception {
        group.addCourse("Mathematics");
        when(teacherRepository.findById("nope")).thenReturn(Optional.empty());

        Map<String, Object> body = Map.of("teacherId", "nope");
        mockMvc.perform(put("/api/groups/G1/courses/Mathematics/default-teacher")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
    }

    @Test
    public void setDefaultTeacher_nullTeacherId_clearsIt() throws Exception {
        group.addCourse("Mathematics");
        group.getCourses().stream().findFirst().orElseThrow().setDefaultTeacherId("T1");

        Map<String, Object> body = new HashMap<>();
        body.put("teacherId", null);
        mockMvc.perform(put("/api/groups/G1/courses/Mathematics/default-teacher")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTeacherId").doesNotExist());
        verify(groupRepository).save(group);
    }

    @Test
    public void setDefaultTeacher_courseNotLinked_returns404() throws Exception {
        Map<String, Object> body = Map.of("teacherId", "T1");
        mockMvc.perform(put("/api/groups/G1/courses/Mathematics/default-teacher")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
    }
}
