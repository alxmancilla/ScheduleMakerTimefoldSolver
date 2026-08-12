package com.example.web.service;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BlockGenerationServiceTest {

    @Mock
    private StudentGroupRepository studentGroupRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseBlockAssignmentRepository assignmentRepository;

    @InjectMocks
    private BlockGenerationService service;

    @Before
    public void setUp() {
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CourseEntity course(String id, String name, int hours, String component, String roomReq) {
        CourseEntity c = new CourseEntity();
        c.setId(id);
        c.setName(name);
        c.setRequiredHoursPerWeek(hours);
        c.setComponent(component);
        c.setRoomRequirement(roomReq);
        return c;
    }

    @Test
    public void basicasCourseWithTwoHours_splitsIntoTwoOneHourBlocks() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        assertEquals(0, result.getGroupCoursesSkippedExisting());
        assertTrue(result.getWarnings().isEmpty());

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();
        assertEquals("G1_C1_0", saved.get(0).getId());
        assertEquals(Integer.valueOf(1), saved.get(0).getBlockLength());
        assertEquals("G1_C1_1", saved.get(1).getId());
        assertEquals(Integer.valueOf(1), saved.get(1).getBlockLength());
        assertEquals("estándar", saved.get(0).getSatisfiesRoomType());
        assertEquals(Boolean.FALSE, saved.get(0).getPinned());
    }

    @Test
    public void nonBasicasFiveHours_decomposesIntoTwoTwoOne() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Welding");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Welding"))
                .thenReturn(Optional.of(course("C2", "Welding", 5, "TEM", "taller")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C2")).thenReturn(false);

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(3, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(3)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(2, 2, 1), lengths);
    }

    @Test
    public void groupCourseWithExistingBlocks_isSkipped() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 4, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(true);

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(0, result.getBlocksCreated());
        assertEquals(1, result.getGroupCoursesSkippedExisting());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    public void groupCourseReferencingUnknownCourse_addsWarningAndSkips() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Nonexistent Course");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Nonexistent Course")).thenReturn(Optional.empty());

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(0, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("not found"));
        verify(assignmentRepository, never()).existsByGroupIdAndCourseId(anyString(), anyString());
    }

    @Test
    public void noGroups_returnsZeroResult() {
        when(studentGroupRepository.findAll()).thenReturn(List.of());
        BlockGenerationService.GenerationResult result = service.generateBlocks();
        assertEquals(0, result.getBlocksCreated());
        assertEquals(0, result.getGroupCoursesSkippedExisting());
        assertTrue(result.getWarnings().isEmpty());
    }
}
