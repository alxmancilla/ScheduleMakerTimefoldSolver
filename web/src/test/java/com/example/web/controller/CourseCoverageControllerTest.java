package com.example.web.controller;

import com.example.web.entity.GroupCourseTeacherEntity;
import com.example.web.repository.GroupCourseTeacherRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for {@link CourseCoverageController}. Uses the MVC slice
 * with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CourseCoverageController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CourseCoverageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupCourseTeacherRepository groupCourseTeacherRepository;

    @Test
    public void getCoverage_returnsList() throws Exception {
        GroupCourseTeacherEntity complete = new GroupCourseTeacherEntity(
                "G1", "Group One", "C1", "Math", "MATH", 4, 1, "Core", "Standard",
                "T1", "Ada Lovelace", 4L, 4L, "Lun 7-8, Mar 7-8", "AULA 1", "Complete");
        // teacherId null - a group_course pair with no blocks generated yet at all.
        GroupCourseTeacherEntity notScheduled = new GroupCourseTeacherEntity(
                "G1", "Group One", "C2", "Physics", "PHYS", 3, 1, "Core", "Standard",
                null, null, 0L, 0L, null, null, "Not Scheduled");
        when(groupCourseTeacherRepository.findAll()).thenReturn(List.of(complete, notScheduled));

        mockMvc.perform(get("/api/course-coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].groupId").value("G1"))
                .andExpect(jsonPath("$[0].schedulingStatus").value("Complete"))
                .andExpect(jsonPath("$[1].teacherId").doesNotExist())
                .andExpect(jsonPath("$[1].schedulingStatus").value("Not Scheduled"));
    }
}
