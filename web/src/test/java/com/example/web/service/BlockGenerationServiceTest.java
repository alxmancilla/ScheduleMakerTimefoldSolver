package com.example.web.service;

import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.ComponentBlockRuleRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
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
    @Mock
    private ComponentBlockRuleRepository componentBlockRuleRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private TeacherRepository teacherRepository;

    @InjectMocks
    private BlockGenerationService service;

    @Before
    public void setUp() {
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roomRequirementRepository.findByCourseIdOrderByPriority(anyString())).thenReturn(List.of());
        when(blockTemplateRepository.findApplicableTemplates(anyString(), anyString())).thenReturn(List.of());
        // No rule configured by default (falls back to the size-2 default), except BASICAS,
        // which is seeded with preferredBlockSize=1 in the real migration/schema too.
        when(componentBlockRuleRepository.findById(anyString())).thenReturn(Optional.empty());
        when(componentBlockRuleRepository.findById("BASICAS"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("BASICAS", 1, 1)));
        when(roomRepository.findById(anyString())).thenReturn(Optional.empty());
        when(teacherRepository.findById(anyString())).thenReturn(Optional.empty());
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
    public void groupWithCompatiblePreferredRoom_defaultsGeneratedBlocksToIt() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.setPreferredRoomName("ROOM1");
        group.addCourse("Mathematics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));

        service.generateBlocks();

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        for (CourseBlockAssignmentEntity block : captor.getAllValues()) {
            assertEquals("ROOM1", block.getRoomName());
        }
    }

    @Test
    public void groupCourseWithDefaultTeacher_appliesItToEveryGeneratedBlock() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics");
        group.getCourses().stream().findFirst().orElseThrow().setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);

        service.generateBlocks();

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        for (CourseBlockAssignmentEntity block : captor.getAllValues()) {
            assertEquals("T1", block.getTeacherId());
        }
    }

    @Test
    public void defaultTeacherRequiredRoom_takesPrecedenceOverGroupPreferredRoom() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.setPreferredRoomName("GROUPROOM");
        group.addCourse("Mathematics");
        group.getCourses().stream().findFirst().orElseThrow().setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        // No stub for GROUPROOM: the teacher's required room short-circuits before
        // defaultRoomFor ever falls back to checking the group's preferred room.
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("TEACHERROOM");
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("TEACHERROOM"))
                .thenReturn(Optional.of(new RoomEntity("TEACHERROOM", "Building B", "estándar")));

        service.generateBlocks();

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        for (CourseBlockAssignmentEntity block : captor.getAllValues()) {
            assertEquals("TEACHERROOM", block.getRoomName());
        }
    }

    @Test
    public void groupWithIncompatiblePreferredRoom_leavesRoomUnset() {
        // A mixto-required block can't default to a plain estándar room.
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.setPreferredRoomName("ROOM1");
        group.addCourse("Chemistry");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Chemistry"))
                .thenReturn(Optional.of(course("C4", "Chemistry", 2, "BASICAS", "mixto")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C4")).thenReturn(false);
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));

        service.generateBlocks();

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        for (CourseBlockAssignmentEntity block : captor.getAllValues()) {
            assertEquals(null, block.getRoomName());
        }
    }

    @Test
    public void roomRequirementDefaultPreferredRoom_takesPrecedenceOverGroupPreference() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.setPreferredRoomName("ROOM1");
        group.addCourse("Computing");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Computing"))
                .thenReturn(Optional.of(course("C5", "Computing", 2, "TIA", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C5")).thenReturn(false);
        when(roomRequirementRepository.findByCourseIdOrderByPriority("C5")).thenReturn(List.of(
                new CourseRoomRequirementEntity("C5", "estándar", 2, 1, "CC1")));
        // No roomRepository stub needed: the requirement's own defaultPreferredRoom
        // ("CC1") short-circuits defaultRoomFor() before it would look up ROOM1.

        service.generateBlocks();

        // 2h at the default block size of 2 -> a single block.
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(1)).save(captor.capture());
        assertEquals("CC1", captor.getValue().getRoomName());
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
        assertEquals("CC 1", saved.get(0).getPreferredRoomHint());

        assertEquals("G1_C3_1", saved.get(1).getId());
        assertEquals(Integer.valueOf(2), saved.get(1).getBlockLength());
        assertEquals("centro de cómputo", saved.get(1).getSatisfiesRoomType());

        assertEquals("G1_C3_2", saved.get(2).getId());
        assertEquals(Integer.valueOf(1), saved.get(2).getBlockLength());
        assertEquals("estándar", saved.get(2).getSatisfiesRoomType());
        assertEquals(null, saved.get(2).getPreferredRoomHint());
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
                new CourseRoomRequirementEntity("C1", "mixto", 2, 1, null)));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();
        assertEquals(Integer.valueOf(1), saved.get(0).getBlockLength());
        assertEquals(Integer.valueOf(1), saved.get(1).getBlockLength());
        assertEquals("mixto", saved.get(0).getSatisfiesRoomType());
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
        assertEquals("TALLER 1", saved.get(0).getPreferredRoomHint());
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

    @Test
    public void componentWithConfiguredBlockSize_packsAtThatSizeInsteadOfDefault() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Cybersecurity");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Cybersecurity"))
                .thenReturn(Optional.of(course("C3", "Cybersecurity", 9, "TCS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C3")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TCS"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TCS", 4, 2)));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        // 9h at a preferred size of 4 -> [4, 4, 1], not the size-2 default's [2, 2, 2, 2, 1]
        assertEquals(3, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(3)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(4, 4, 1), lengths);
    }

    @Test
    public void componentWithNoConfiguredRule_fallsBackToDefaultSizeTwo() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Electronics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Electronics"))
                .thenReturn(Optional.of(course("C4", "Electronics", 3, "TELE", "taller")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C4")).thenReturn(false);
        // No stub for "TELE" - componentBlockRuleRepository.findById returns empty by default.

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(2, 1), lengths);
    }
}
