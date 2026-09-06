package com.example.web.service;

import com.example.web.dto.AssignmentMoveValidationResponse;
import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.ComponentBlockRuleRepository;
import com.example.web.repository.ConstraintConfigRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.SemesterHourLimitRepository;
import com.example.web.repository.TeacherRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link AssignmentMoveValidationService}'s five checks and, in
 * particular, its severity-awareness: a check whose SchoolConstraintProvider
 * counterpart is currently configured SOFT (a constraint_config row exists -
 * see scheduler-common's ConfigurableHardConstraints) must be reported as a
 * warning, not a blocking violation.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssignmentMoveValidationServiceTest {

    @Mock
    private CourseBlockAssignmentRepository assignmentRepository;
    @Mock
    private BlockTimeslotRepository timeslotRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ComponentBlockRuleRepository componentBlockRuleRepository;
    @Mock
    private SemesterHourLimitRepository semesterHourLimitRepository;
    @Mock
    private ConstraintConfigRepository constraintConfigRepository;

    @InjectMocks
    private AssignmentMoveValidationService service;

    private CourseBlockAssignmentEntity assignment;
    private BlockTimeslotEntity target;
    private TeacherEntity teacher;
    private CourseEntity course;

    @Before
    public void setUp() {
        assignment = new CourseBlockAssignmentEntity();
        assignment.setId("A1");
        assignment.setGroupId("G1");
        assignment.setCourseId("C1");
        assignment.setBlockLength(1);
        assignment.setTeacherId("T1");
        assignment.setRoomName("R1");

        target = new BlockTimeslotEntity(1, 8, 1);
        target.setId("TS_TARGET");

        teacher = new TeacherEntity("T1", "Ana", "Lopez", 30);
        // Available all day Monday 7-15 by default; individual tests narrow this.
        for (int h = 7; h < 15; h++) {
            teacher.addAvailability(1, h);
        }

        course = new CourseEntity("C1", "Math", "Standard", 4);

        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(timeslotRepository.findById("TS_TARGET")).thenReturn(Optional.of(target));
        when(assignmentRepository.findAll()).thenReturn(new ArrayList<>(List.of(assignment)));
        when(timeslotRepository.findAll()).thenReturn(new ArrayList<>(List.of(target)));
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        // No overrides configured for anything by default - every
        // configurable constraint stays HARD unless a test says otherwise.
        when(constraintConfigRepository.existsById(anyString())).thenReturn(false);
    }

    @Test
    public void cleanMove_noViolationsNoWarnings() {
        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().isEmpty());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void lengthMismatch_isAViolation() {
        assignment.setBlockLength(2);
        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertEquals(1, response.getViolations().size());
        assertTrue(response.getViolations().get(0).contains("length"));
    }

    @Test
    public void pinningWithoutARoom_isAViolation() {
        assignment.setRoomName(null);
        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", true);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("pin")));
    }

    @Test
    public void teacherDoubleBooking_isAViolation() {
        CourseBlockAssignmentEntity other = new CourseBlockAssignmentEntity();
        other.setId("A2");
        other.setGroupId("G2");
        other.setCourseId("C2");
        other.setTeacherId("T1");
        other.setBlockTimeslotId("TS_TARGET");
        when(assignmentRepository.findAll()).thenReturn(new ArrayList<>(List.of(assignment, other)));

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("Teacher double-booking")));
    }

    @Test
    public void roomDoubleBooking_isAViolation() {
        CourseBlockAssignmentEntity other = new CourseBlockAssignmentEntity();
        other.setId("A2");
        other.setGroupId("G2");
        other.setCourseId("C2");
        other.setTeacherId("T2");
        other.setRoomName("R1");
        other.setBlockTimeslotId("TS_TARGET");
        when(assignmentRepository.findAll()).thenReturn(new ArrayList<>(List.of(assignment, other)));

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("Room double-booking")));
    }

    @Test
    public void teacherUnavailable_defaultsToAViolation() {
        TeacherEntity unavailableTeacher = new TeacherEntity("T1", "Ana", "Lopez", 30);
        // No availability added at all - target slot's hour is never covered.
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(unavailableTeacher));

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("not available")));
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void teacherUnavailable_isOnlyAWarningWhenConstraintIsConfiguredSoft() {
        TeacherEntity unavailableTeacher = new TeacherEntity("T1", "Ana", "Lopez", 30);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(unavailableTeacher));
        when(constraintConfigRepository.existsById("Teacher must be available for entire block")).thenReturn(true);

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().isEmpty());
        assertTrue(response.getWarnings().stream().anyMatch(w -> w.contains("not available")));
    }

    @Test
    public void semesterHourLimitHard_exceeded_isAViolation() {
        course.setSemester(1);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        when(semesterHourLimitRepository.findById(1))
                .thenReturn(Optional.of(new SemesterHourLimitEntity(1, 8, "HARD")));
        // target ends at 9:00, past the 8:00 limit.

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("semester")));
    }

    @Test
    public void semesterHourLimitSoft_exceeded_isOnlyAWarning() {
        course.setSemester(1);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        when(semesterHourLimitRepository.findById(1))
                .thenReturn(Optional.of(new SemesterHourLimitEntity(1, 8, "SOFT")));

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().isEmpty());
        assertTrue(response.getWarnings().stream().anyMatch(w -> w.contains("semester")));
    }

    @Test
    public void semesterHourLimitNotExceeded_noViolationOrWarning() {
        course.setSemester(1);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        when(semesterHourLimitRepository.findById(1))
                .thenReturn(Optional.of(new SemesterHourLimitEntity(1, 9, "HARD")));
        // target ends at 9:00, exactly at (not past) the limit.

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().isEmpty());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void maxBlocksPerDayExceeded_defaultsToAViolation() {
        course.setDesignation("Core");
        when(componentBlockRuleRepository.findById("Core"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("Core", 1, 1)));
        CourseBlockAssignmentEntity sameDayOther = new CourseBlockAssignmentEntity();
        sameDayOther.setId("A2");
        sameDayOther.setGroupId("G1");
        sameDayOther.setCourseId("C1");
        BlockTimeslotEntity otherSlot = new BlockTimeslotEntity(1, 10, 1);
        otherSlot.setId("TS_OTHER");
        sameDayOther.setBlockTimeslotId("TS_OTHER");
        when(assignmentRepository.findAll()).thenReturn(new ArrayList<>(List.of(assignment, sameDayOther)));
        when(timeslotRepository.findAll()).thenReturn(new ArrayList<>(List.of(target, otherSlot)));

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("max 1")));
    }

    @Test
    public void maxBlocksPerDayExceeded_isOnlyAWarningWhenConstraintIsConfiguredSoft() {
        course.setDesignation("Core");
        when(componentBlockRuleRepository.findById("Core"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("Core", 1, 1)));
        CourseBlockAssignmentEntity sameDayOther = new CourseBlockAssignmentEntity();
        sameDayOther.setId("A2");
        sameDayOther.setGroupId("G1");
        sameDayOther.setCourseId("C1");
        // Immediately after target's 8-9 slot (no gap), so only the max-per-day
        // check fires here, not the separately-configured consecutiveness one.
        BlockTimeslotEntity otherSlot = new BlockTimeslotEntity(1, 9, 1);
        otherSlot.setId("TS_OTHER");
        sameDayOther.setBlockTimeslotId("TS_OTHER");
        when(assignmentRepository.findAll()).thenReturn(new ArrayList<>(List.of(assignment, sameDayOther)));
        when(timeslotRepository.findAll()).thenReturn(new ArrayList<>(List.of(target, otherSlot)));
        when(constraintConfigRepository.existsById("Maximum blocks per course per group per day")).thenReturn(true);

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().isEmpty());
        assertTrue(response.getWarnings().stream().anyMatch(w -> w.contains("max 1")));
    }

    @Test
    public void nonConsecutiveSameDayBlocks_defaultsToAViolation() {
        // Target at 8-9, another block of the same (group, course) at 11-12 -
        // a gap, so not consecutive. Bump the per-day cap so only the
        // consecutiveness check (not the max-per-day one) fires.
        course.setDesignation("Core");
        when(componentBlockRuleRepository.findById("Core"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("Core", 1, 5)));
        CourseBlockAssignmentEntity sameDayOther = new CourseBlockAssignmentEntity();
        sameDayOther.setId("A2");
        sameDayOther.setGroupId("G1");
        sameDayOther.setCourseId("C1");
        BlockTimeslotEntity otherSlot = new BlockTimeslotEntity(1, 11, 1);
        otherSlot.setId("TS_OTHER");
        sameDayOther.setBlockTimeslotId("TS_OTHER");
        when(assignmentRepository.findAll()).thenReturn(new ArrayList<>(List.of(assignment, sameDayOther)));
        when(timeslotRepository.findAll()).thenReturn(new ArrayList<>(List.of(target, otherSlot)));

        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().anyMatch(v -> v.contains("consecutiveness")));
    }

    @Test
    public void singleBlockThatDay_noDayShapeChecksFire() {
        AssignmentMoveValidationResponse response = service.validate("A1", "TS_TARGET", false);
        assertTrue(response.getViolations().stream().noneMatch(v -> v.contains("consecutiveness") || v.contains("max")));
    }
}
