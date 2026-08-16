package com.example.web.service;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
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
    @Mock
    private CourseRoomRequirementRepository roomRequirementRepository;
    @Mock
    private CourseBlockTemplateRepository blockTemplateRepository;

    @InjectMocks
    private BlockGenerationService service;

    @Before
    public void setUp() {
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roomRequirementRepository.findByCourseIdOrderByPriority(anyString())).thenReturn(List.of());
        when(blockTemplateRepository.findApplicableTemplates(anyString(), anyString())).thenReturn(List.of());
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

    @Test
    public void courseWithRoomRequirements_decomposesEachRequirementSeparately() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Cybersecurity");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        // requiredHoursPerWeek/roomRequirement on the course itself are ignored once
        // dual requirements exist - only the requirement rows' own hours/room type count.
        when(courseRepository.findByName("Cybersecurity"))
                .thenReturn(Optional.of(course("C3", "Cybersecurity", 999, "TCS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C3")).thenReturn(false);
        when(roomRequirementRepository.findByCourseIdOrderByPriority("C3")).thenReturn(List.of(
                new CourseRoomRequirementEntity("C3", "centro de cómputo", 4, 1, "CC 1"),
                new CourseRoomRequirementEntity("C3", "estándar", 1, 2, null)));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        // 4h CC -> [2, 2], 1h estándar -> [1]
        assertEquals(3, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(3)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();

        assertEquals("G1_C3_0", saved.get(0).getId());
        assertEquals(Integer.valueOf(2), saved.get(0).getBlockLength());
        assertEquals("centro de cómputo", saved.get(0).getSatisfiesRoomType());
        assertEquals("CC 1", saved.get(0).getPreferredRoomName());

        assertEquals("G1_C3_1", saved.get(1).getId());
        assertEquals(Integer.valueOf(2), saved.get(1).getBlockLength());
        assertEquals("centro de cómputo", saved.get(1).getSatisfiesRoomType());

        assertEquals("G1_C3_2", saved.get(2).getId());
        assertEquals(Integer.valueOf(1), saved.get(2).getBlockLength());
        assertEquals("estándar", saved.get(2).getSatisfiesRoomType());
        assertEquals(null, saved.get(2).getPreferredRoomName());
    }

    @Test
    public void basicasCourseWithRoomRequirementOfTwoHours_splitsThatRequirementIntoTwoOneHourBlocks() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(roomRequirementRepository.findByCourseIdOrderByPriority("C1")).thenReturn(List.of(
                new CourseRoomRequirementEntity("C1", "laboratorio", 2, 1, null)));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();
        assertEquals(Integer.valueOf(1), saved.get(0).getBlockLength());
        assertEquals(Integer.valueOf(1), saved.get(1).getBlockLength());
        assertEquals("laboratorio", saved.get(0).getSatisfiesRoomType());
    }

    @Test
    public void courseWithBlockTemplates_usesTemplatesInsteadOfGenericDecomposition() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Welding");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        // requiredHoursPerWeek/roomRequirement on the course are ignored once templates
        // exist - only the templates' own fields count, and room_requirement rows (if
        // any existed) would be ignored too, since templates take full precedence.
        when(courseRepository.findByName("Welding"))
                .thenReturn(Optional.of(course("C2", "Welding", 999, "TEM", "taller")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C2")).thenReturn(false);
        when(blockTemplateRepository.findApplicableTemplates("C2", "G1")).thenReturn(List.of(
                new CourseBlockTemplateEntity("C2", "G1", 0, 3, "taller", "TALLER 1", null, false, null),
                new CourseBlockTemplateEntity("C2", "G1", 1, 2, "taller", null, null, false, null)));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();

        assertEquals("G1_C2_0", saved.get(0).getId());
        assertEquals(Integer.valueOf(3), saved.get(0).getBlockLength());
        assertEquals("taller", saved.get(0).getSatisfiesRoomType());
        assertEquals("TALLER 1", saved.get(0).getPreferredRoomName());
        // A template's preferred room is pre-assigned onto roomName directly (room is
        // never solver-assigned), not left as just a soft preference.
        assertEquals("TALLER 1", saved.get(0).getRoomName());
        assertEquals(Boolean.FALSE, saved.get(0).getPinned());

        assertEquals("G1_C2_1", saved.get(1).getId());
        assertEquals(Integer.valueOf(2), saved.get(1).getBlockLength());
    }

    @Test
    public void pinnedBlockTemplate_setsTimeslotAndPinnedOnGeneratedBlock() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Welding");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Welding"))
                .thenReturn(Optional.of(course("C2", "Welding", 3, "TEM", "taller")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C2")).thenReturn(false);
        when(blockTemplateRepository.findApplicableTemplates("C2", "G1")).thenReturn(List.of(
                new CourseBlockTemplateEntity("C2", "G1", 0, 3, "taller", "TALLER 1", 1, true, "block_mon_7_3")));

        service.generateBlocks();

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        CourseBlockAssignmentEntity saved = captor.getValue();
        assertEquals(Boolean.TRUE, saved.getPinned());
        assertEquals("block_mon_7_3", saved.getBlockTimeslotId());
        assertEquals("TALLER 1", saved.getRoomName());
    }

    @Test
    public void groupSpecificTemplate_winsOverWildcardTemplateForSameBlockIndex() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Welding");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Welding"))
                .thenReturn(Optional.of(course("C2", "Welding", 2, "TEM", "taller")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C2")).thenReturn(false);
        // Both apply to (C2, G1) per the applicable-templates query: a wildcard
        // (groupId null) and a G1-specific override, same blockIndex.
        when(blockTemplateRepository.findApplicableTemplates("C2", "G1")).thenReturn(List.of(
                new CourseBlockTemplateEntity("C2", null, 0, 1, "estándar", null, null, false, null),
                new CourseBlockTemplateEntity("C2", "G1", 0, 2, "taller", "TALLER 1", null, false, null)));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        CourseBlockAssignmentEntity saved = captor.getValue();
        assertEquals(Integer.valueOf(2), saved.getBlockLength());
        assertEquals("taller", saved.getSatisfiesRoomType());
    }
}
