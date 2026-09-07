package com.example.web.controller;

import com.example.web.entity.*;
import com.example.web.repository.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ScheduleController}, covering both the default
 * (no runId - reads through course_block_assignment_current) and the
 * run-selection path (reads a specific run's own frozen schedule_run_result
 * snapshot directly, independent of the live course_block_assignment table).
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;
    @MockBean
    private CourseBlockAssignmentCurrentRepository assignmentCurrentRepository;
    @MockBean
    private ScheduleRunRepository scheduleRunRepository;
    @MockBean
    private ScheduleRunResultRepository scheduleRunResultRepository;
    @MockBean
    private ScheduleRunViolationRepository scheduleRunViolationRepository;
    @MockBean
    private ScheduleRunViolationAssignmentRepository scheduleRunViolationAssignmentRepository;
    @MockBean
    private BlockTimeslotRepository timeslotRepository;
    @MockBean
    private CourseRepository courseRepository;
    @MockBean
    private TeacherRepository teacherRepository;
    @MockBean
    private RoomRepository roomRepository;
    @MockBean
    private StudentGroupRepository groupRepository;
    @MockBean
    private AppUserRepository appUserRepository;

    private BlockTimeslotEntity timeslot(String id) {
        BlockTimeslotEntity ts = new BlockTimeslotEntity(1, 8, 1);
        ts.setId(id);
        return ts;
    }

    private CourseEntity course(String id, String name) {
        return new CourseEntity(id, name, "Standard", 1);
    }

    @Test
    public void getScheduleRuns_returnsRunsMappedToDTO() throws Exception {
        ScheduleRunEntity run = new ScheduleRunEntity(7, LocalDateTime.of(2026, 8, 23, 10, 0), -3, -50, 5, 2);
        when(scheduleRunRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(run));

        mockMvc.perform(get("/api/schedule/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].hardScore").value(-3))
                .andExpect(jsonPath("$[0].softScore").value(-50))
                .andExpect(jsonPath("$[0].minutesSpentLimit").value(5))
                .andExpect(jsonPath("$[0].unimprovedMinutesSpentLimit").value(2));
    }

    @Test
    public void getScheduleView_noRunId_readsFromCurrentView() throws Exception {
        CourseBlockAssignmentCurrentEntity a = new CourseBlockAssignmentCurrentEntity(
                "a1", "G1", "C1", 1, false, null, "TS1", null, null, null);
        when(assignmentCurrentRepository.findAll()).thenReturn(List.of(a));
        when(timeslotRepository.findAll()).thenReturn(List.of(timeslot("TS1")));
        when(courseRepository.findAll()).thenReturn(List.of(course("C1", "Math")));
        when(teacherRepository.findAll()).thenReturn(List.of());
        when(roomRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());
        when(assignmentRepository.findUnassignedBlocks()).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].id").value("a1"))
                .andExpect(jsonPath("$.entries[0].courseName").value("Math"))
                .andExpect(jsonPath("$.entries[0].courseId").value("C1"));
    }

    @Test
    public void getScheduleView_carriesSatisfiesRoomTypeThrough() throws Exception {
        // Needed by the Schedule grid's admin-only room reassignment field to
        // filter room choices to the block's own type - was silently dropped
        // before ResolvedAssignment/ScheduleEntry both carried it.
        CourseBlockAssignmentCurrentEntity a = new CourseBlockAssignmentCurrentEntity(
                "a1", "G1", "C1", 1, false, null, "TS1", null, "Mixed", null);
        when(assignmentCurrentRepository.findAll()).thenReturn(List.of(a));
        when(timeslotRepository.findAll()).thenReturn(List.of(timeslot("TS1")));
        when(courseRepository.findAll()).thenReturn(List.of(course("C1", "Math")));
        when(teacherRepository.findAll()).thenReturn(List.of());
        when(roomRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());
        when(assignmentRepository.findUnassignedBlocks()).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].satisfiesRoomType").value("Mixed"));
    }

    @Test
    public void getScheduleView_withRunId_readsFrozenSnapshotNotLiveTable() throws Exception {
        // Run 5's snapshot says teacher T-OLD taught this block - even though the
        // live course_block_assignment table (never consulted for this path) might
        // say something different by now, e.g. after a reassignment.
        ScheduleRunResultEntity snapshot = new ScheduleRunResultEntity(
                5, "a1", "TS1", "G1", "C1", 1, false, "T-OLD", "R1", null, null);
        when(scheduleRunResultRepository.findByScheduleRunId(5)).thenReturn(List.of(snapshot));

        when(timeslotRepository.findAll()).thenReturn(List.of(timeslot("TS1")));
        when(courseRepository.findAll()).thenReturn(List.of(course("C1", "Math")));
        when(teacherRepository.findAll()).thenReturn(List.of(new TeacherEntity("T-OLD", "Old", "Teacher", 40)));
        when(roomRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());
        when(assignmentRepository.findUnassignedBlocks()).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule/view").param("runId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.entries[0].id").value("a1"))
                .andExpect(jsonPath("$.entries[0].teacherId").value("T-OLD"));

        // The raw input table is never touched for a runId-scoped lookup.
        org.mockito.Mockito.verifyNoInteractions(assignmentCurrentRepository);
    }

    @Test
    public void getScheduleViolations_explicitRunId_splitsHardAndSoft() throws Exception {
        when(scheduleRunViolationRepository.findByScheduleRunId(9)).thenReturn(List.of(
                new ScheduleRunViolationEntity(1L, 9, "No teacher double-booking", true, "A <-> B"),
                new ScheduleRunViolationEntity(2L, 9, "Prefer block's specified room", false, "A prefers R1")));
        when(scheduleRunViolationAssignmentRepository.findByViolationIdIn(List.of(1L, 2L))).thenReturn(List.of(
                new ScheduleRunViolationAssignmentEntity(1L, "a1"),
                new ScheduleRunViolationAssignmentEntity(1L, "a2"),
                new ScheduleRunViolationAssignmentEntity(2L, "a1")));

        mockMvc.perform(get("/api/schedule/violations").param("runId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(9))
                .andExpect(jsonPath("$.hard", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.hard[0].constraintName").value("No teacher double-booking"))
                .andExpect(jsonPath("$.hard[0].description").value("A <-> B"))
                .andExpect(jsonPath("$.hard[0].assignmentIds", org.hamcrest.Matchers.contains("a1", "a2")))
                .andExpect(jsonPath("$.soft", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.soft[0].constraintName").value("Prefer block's specified room"))
                .andExpect(jsonPath("$.soft[0].assignmentIds", org.hamcrest.Matchers.contains("a1")));
    }

    @Test
    public void getScheduleViolations_violationWithNoLinkedAssignments_hasEmptyAssignmentIds() throws Exception {
        // A run predating this feature (added 2026-09-07), or a purely aggregate
        // violation instance with no assignmentIds recorded, should still resolve
        // cleanly to an empty list rather than null or an error.
        when(scheduleRunViolationRepository.findByScheduleRunId(9)).thenReturn(List.of(
                new ScheduleRunViolationEntity(1L, 9, "No teacher double-booking", true, "A <-> B")));
        when(scheduleRunViolationAssignmentRepository.findByViolationIdIn(List.of(1L))).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule/violations").param("runId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hard[0].assignmentIds", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    public void getScheduleViolations_noRunId_resolvesToLatestRun() throws Exception {
        ScheduleRunEntity latest = new ScheduleRunEntity(12, LocalDateTime.now(), 0, -5, 5, 2);
        when(scheduleRunRepository.findTopByOrderByCreatedAtDesc()).thenReturn(java.util.Optional.of(latest));
        when(scheduleRunViolationRepository.findByScheduleRunId(12)).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(12))
                .andExpect(jsonPath("$.hard", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.soft", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    public void getScheduleViolations_noRunsExistAtAll_returnsEmptyWithNullRunId() throws Exception {
        when(scheduleRunRepository.findTopByOrderByCreatedAtDesc()).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").doesNotExist())
                .andExpect(jsonPath("$.hard", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.soft", org.hamcrest.Matchers.hasSize(0)));

        org.mockito.Mockito.verifyNoInteractions(scheduleRunViolationRepository);
    }

}
