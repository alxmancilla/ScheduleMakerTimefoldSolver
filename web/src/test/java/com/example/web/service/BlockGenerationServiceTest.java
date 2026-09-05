package com.example.web.service;

import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.entity.GroupRoomRangeEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.ComponentBlockRuleRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.SemesterHourLimitRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import com.example.web.repository.GroupRoomRangeRepository;
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
    @Mock
    private GroupRoomRangeRepository groupRoomRangeRepository;
    @Mock
    private BlockTimeslotRepository blockTimeslotRepository;
    @Mock
    private SemesterHourLimitRepository semesterHourLimitRepository;

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
        when(groupRoomRangeRepository.findByGroupIdAndRoomType(anyString(), anyString())).thenReturn(List.of());
        when(assignmentRepository.findByTeacherId(anyString())).thenReturn(List.of());
        when(assignmentRepository.findByGroupId(anyString())).thenReturn(List.of());
        when(assignmentRepository.findByRoomName(anyString())).thenReturn(List.of());
        when(semesterHourLimitRepository.findById(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
    }

    private CourseEntity course(String id, String name, int hours, String designation, String roomReq) {
        CourseEntity c = new CourseEntity();
        c.setId(id);
        c.setName(name);
        c.setRequiredHoursPerWeek(hours);
        c.setDesignation(designation);
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
    public void template_pinRequestedButNoRoomResolvable_savedUnpinnedWithWarning() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);

        CourseBlockTemplateEntity template = new CourseBlockTemplateEntity("C1", null, 0, 2, "estándar",
                null, null, true, null);
        when(blockTemplateRepository.findApplicableTemplates("C1", "G1")).thenReturn(List.of(template));
        // No group.preferredRoomName, no teacher, so defaultRoomFor() has nothing to resolve.

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("no compatible room could be resolved"));

        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getPinned());
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
        group.addCourse("Mathematics");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        when(groupRoomRangeRepository.findByGroupIdAndRoomType("G1", "estándar"))
                .thenReturn(List.of(new GroupRoomRangeEntity("G1", "estándar", "ROOM1")));

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
        group.addCourse("Mathematics");
        group.getCourses().stream().findFirst().orElseThrow().setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        // No stub for the group's own range lookup: the teacher's required room
        // short-circuits before defaultRoomFor ever falls back to checking the
        // group's range (default stub in setUp() returns empty either way).
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
        // A mixto-required block can't default to a plain estándar room, even
        // if (a data error) it's curated under the group's "mixto" range.
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Chemistry");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Chemistry"))
                .thenReturn(Optional.of(course("C4", "Chemistry", 2, "BASICAS", "mixto")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C4")).thenReturn(false);
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        when(groupRoomRangeRepository.findByGroupIdAndRoomType("G1", "mixto"))
                .thenReturn(List.of(new GroupRoomRangeEntity("G1", "mixto", "ROOM1")));

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
        group.addCourse("Computing");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Computing"))
                .thenReturn(Optional.of(course("C5", "Computing", 2, "TIA", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C5")).thenReturn(false);
        when(roomRequirementRepository.findByCourseIdOrderByPriority("C5")).thenReturn(List.of(
                new CourseRoomRequirementEntity("C5", "estándar", 2, 1, "CC1")));
        // No stub for the group's own range lookup needed: the requirement's
        // own defaultPreferredRoom ("CC1") short-circuits defaultRoomFor()
        // before it would look up the group's range.

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
    public void groupCourseReferencingInactiveCourse_addsWarningAndSkips() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Retired Course");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        CourseEntity inactive = course("C1", "Retired Course", 4, "BASICAS", "estándar");
        inactive.setActive(false);
        when(courseRepository.findByName("Retired Course")).thenReturn(Optional.of(inactive));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(0, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("inactive"));
        verify(assignmentRepository, never()).existsByGroupIdAndCourseId(anyString(), anyString());
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

    // ---- Scenario 2: availability-aware shape adaptation ----
    // All of these explicitly mark the teacher non-exclusive (findByTeacherId
    // returns an existing row) so shape adaptation is tested in isolation from
    // Scenario 1's pinning, which is covered separately below.

    @Test
    public void teacherAvailabilityAlreadyFitsNaiveShape_unchangedBehavior() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(assignmentRepository.findByTeacherId("T1")).thenReturn(List.of(new CourseBlockAssignmentEntity()));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.addAvailability(1, 7);
        teacher.addAvailability(2, 7); // BASICAS needs 2 days at maxBlocksPerDay=1 for 2 blocks - exactly fits
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        assertTrue(result.getWarnings().isEmpty());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(1, 1), lengths); // naive BASICAS shape, untouched
    }

    @Test
    public void teacherAvailabilityDoesNotFitNaiveShape_adaptsToLongerBlocks() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        // 5h, BASICAS (preferredSize=1, maxBlocksPerDay=1) -> naive needs 5 distinct days.
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 5, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(assignmentRepository.findByTeacherId("T1")).thenReturn(List.of(new CourseBlockAssignmentEntity()));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        // Only 2 days, each a 4h contiguous window.
        for (int h = 7; h <= 10; h++) {
            teacher.addAvailability(1, h);
            teacher.addAvailability(2, h);
        }
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        // Adapted to size 3: packBlocks(5,3) = [3,2], 2 blocks at 1/day fits 2 days.
        assertEquals(2, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(3, 2), lengths);
    }

    @Test
    public void defaultTeacherIdSetButTeacherNotFound_fallsBackToNaiveShape() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 5, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        // teacherRepository.findById("T1") falls back to setUp()'s Optional.empty() default.

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(5, result.getBlocksCreated()); // naive BASICAS shape: five 1h blocks
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(5)).save(captor.capture());
    }

    @Test
    public void noSizeFitsTeacherAvailability_fallsBackToNaiveShapeWithoutError() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 5, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(assignmentRepository.findByTeacherId("T1")).thenReturn(List.of(new CourseBlockAssignmentEntity()));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.addAvailability(1, 7); // a single lonely hour - nothing can make 5h fit
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(5, result.getBlocksCreated()); // unchanged naive shape - this is a genuinely infeasible pairing
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(5)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(1, 1, 1, 1, 1), lengths);
    }

    // ---- Scenario 1: auto-pin when the teacher's whole load is this one pairing ----

    @Test
    public void exclusiveTeacherWithResolvableRoom_pinsGeneratedBlocks() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "TEM", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));
        // findByTeacherId("T1") stays empty (setUp default) - this is T1's only work.

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1"); // resolves to a single deterministic room
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));

        BlockTimeslotEntity timeslot = new BlockTimeslotEntity();
        timeslot.setId("TS_MON_7_2");
        timeslot.setDayOfWeek(1);
        timeslot.setStartHour(7);
        timeslot.setLengthHours(2);
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(1, 7, 2))
                .thenReturn(Optional.of(timeslot));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertTrue(result.getWarnings().isEmpty());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        // Once from saveBlock, once more from the pinning save.
        verify(assignmentRepository, times(2)).save(captor.capture());
        CourseBlockAssignmentEntity finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(Boolean.TRUE, finalState.getPinned());
        assertEquals("TS_MON_7_2", finalState.getBlockTimeslotId());
        assertEquals("ROOM1", finalState.getRoomName());
    }

    @Test
    public void exclusiveTeacherButAmbiguousRoom_leftUnpinnedWithWarning() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "TEM", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40); // no requiredRoomName
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        // Group's range for "estándar" has 2 rooms - ambiguous, defaultRoomFor() returns null.
        when(groupRoomRangeRepository.findByGroupIdAndRoomType("G1", "estándar")).thenReturn(List.of(
                new GroupRoomRangeEntity("G1", "estándar", "ROOM1"),
                new GroupRoomRangeEntity("G1", "estándar", "ROOM2")));

        BlockTimeslotEntity timeslot = new BlockTimeslotEntity();
        timeslot.setId("TS_MON_7_2");
        timeslot.setDayOfWeek(1);
        timeslot.setStartHour(7);
        timeslot.setLengthHours(2);
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(1, 7, 2))
                .thenReturn(Optional.of(timeslot));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("no single room could be resolved"));
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(1)).save(captor.capture()); // no second (pinning) save
        assertEquals(Boolean.FALSE, captor.getValue().getPinned());
        assertEquals(null, captor.getValue().getBlockTimeslotId());
    }

    @Test
    public void exclusiveTeacherButViolatesSemesterHourLimit_leftUnpinnedWithWarning() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Circuits").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        CourseEntity course = course("C1", "Circuits", 2, "TEM", "estándar");
        course.setSemester(5);
        when(courseRepository.findByName("Circuits")).thenReturn(Optional.of(course));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));
        // Semester 5 has a HARD limit of 14:00; the computed slot (13-15) ends after it.
        when(semesterHourLimitRepository.findById(5))
                .thenReturn(Optional.of(new SemesterHourLimitEntity(5, 14, "HARD")));

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        teacher.addAvailability(1, 13);
        teacher.addAvailability(1, 14);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        BlockTimeslotEntity timeslot = new BlockTimeslotEntity();
        timeslot.setId("TS_MON_13_2");
        timeslot.setDayOfWeek(1);
        timeslot.setStartHour(13);
        timeslot.setLengthHours(2);
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(1, 13, 2))
                .thenReturn(Optional.of(timeslot));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("HARD hour limit"));
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(1)).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getPinned());
    }

    @Test
    public void exclusiveTeacherButRoomAlreadyPinnedElsewhere_leftUnpinnedWithWarning() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "TEM", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        BlockTimeslotEntity candidate = new BlockTimeslotEntity();
        candidate.setId("TS_MON_7_2");
        candidate.setDayOfWeek(1);
        candidate.setStartHour(7);
        candidate.setLengthHours(2);
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(1, 7, 2))
                .thenReturn(Optional.of(candidate));

        // A completely unrelated group already has ROOM1 pinned Monday 7-9 for a
        // different course/teacher - this group's own pinned-conflict check
        // wouldn't catch this, since it only looks at G1's own pinned data.
        CourseBlockAssignmentEntity otherGroupsPinned = new CourseBlockAssignmentEntity();
        otherGroupsPinned.setId("OTHER_G_0");
        otherGroupsPinned.setPinned(true);
        otherGroupsPinned.setRoomName("ROOM1");
        otherGroupsPinned.setBlockTimeslotId("TS_EXISTING");
        when(assignmentRepository.findByRoomName("ROOM1")).thenReturn(List.of(otherGroupsPinned));
        BlockTimeslotEntity existingSlot = new BlockTimeslotEntity();
        existingSlot.setId("TS_EXISTING");
        existingSlot.setDayOfWeek(1);
        existingSlot.setStartHour(7);
        existingSlot.setLengthHours(2);
        when(blockTimeslotRepository.findById("TS_EXISTING")).thenReturn(Optional.of(existingSlot));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("already pinned to another assignment"));
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(1)).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getPinned());
    }

    @Test
    public void nonExclusiveTeacher_neverAttemptsPinningEvenIfAvailabilityWouldAllowIt() {
        StudentGroupEntity groupA = new StudentGroupEntity("GA", "Group A");
        groupA.addCourse("Mathematics").setDefaultTeacherId("T1");
        StudentGroupEntity groupB = new StudentGroupEntity("GB", "Group B");
        groupB.addCourse("Physics").setDefaultTeacherId("T1"); // same teacher, a second commitment
        when(studentGroupRepository.findAll()).thenReturn(List.of(groupA, groupB));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "TEM", "estándar")));
        when(courseRepository.findByName("Physics"))
                .thenReturn(Optional.of(course("C2", "Physics", 2, "TEM", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId(anyString(), anyString())).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        // Deliberately no blockTimeslotRepository stub: a non-exclusive teacher must
        // never even attempt pinning, so it should never be consulted at all.

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        assertTrue(result.getWarnings().isEmpty()); // no pin attempt at all, so no pin-failure warnings either
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture()); // one save per block, no re-save for pinning
        for (CourseBlockAssignmentEntity block : captor.getAllValues()) {
            assertEquals(Boolean.FALSE, block.getPinned());
        }
    }

    @Test
    public void exclusiveTeacherButConflictsWithGroupsExistingPinnedData_leftUnpinned() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "TEM", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));

        BlockTimeslotEntity candidate = new BlockTimeslotEntity();
        candidate.setId("TS_MON_7_2");
        candidate.setDayOfWeek(1);
        candidate.setStartHour(7);
        candidate.setLengthHours(2);
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(1, 7, 2))
                .thenReturn(Optional.of(candidate));

        // The group already has an unrelated course pinned Monday 7-9 (overlaps candidate).
        CourseBlockAssignmentEntity existingPinned = new CourseBlockAssignmentEntity();
        existingPinned.setId("G1_OTHER_0");
        existingPinned.setPinned(true);
        existingPinned.setBlockTimeslotId("TS_EXISTING");
        when(assignmentRepository.findByGroupId("G1")).thenReturn(List.of(existingPinned));
        BlockTimeslotEntity existingSlot = new BlockTimeslotEntity();
        existingSlot.setId("TS_EXISTING");
        existingSlot.setDayOfWeek(1);
        existingSlot.setStartHour(7);
        existingSlot.setLengthHours(3);
        when(blockTimeslotRepository.findById("TS_EXISTING")).thenReturn(Optional.of(existingSlot));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("conflicts with"));
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(1)).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getPinned());
    }

    @Test
    public void exclusiveTeacherButNoMatchingTimeslotExists_leftUnpinnedWithWarning() {
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "TEM", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(componentBlockRuleRepository.findById("TEM"))
                .thenReturn(Optional.of(new ComponentBlockRuleEntity("TEM", 2, 2)));

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));
        // No matching BlockTimeslotEntity for day 1 / hour 7 / length 2 (unstubbed -> empty).

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(1, result.getBlocksCreated());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("no"));
        assertTrue(result.getWarnings().get(0).contains("timeslot exists"));
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(1)).save(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getPinned());
    }

    // ---- Shared calendar across pairings sharing one teacher (Option C) ----

    @Test
    public void sharedTeacher_secondPairingSeesTheFirstPairingsConsumption() {
        // Teacher has 3 days, each a 2h window (6h total). Both groups need 4h of
        // the same BASICAS course from this teacher - not enough total slack for
        // both to get a comfortably-margined shape.
        StudentGroupEntity groupA = new StudentGroupEntity("GA", "Group A");
        groupA.addCourse("Mathematics").setDefaultTeacherId("T1");
        StudentGroupEntity groupB = new StudentGroupEntity("GB", "Group B");
        groupB.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(groupA, groupB));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 4, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId(anyString(), anyString())).thenReturn(false);

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        for (int day = 1; day <= 3; day++) {
            teacher.addAvailability(day, 7);
            teacher.addAvailability(day, 8);
        }
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(6, result.getBlocksCreated()); // GA: 2 blocks, GB: 4 blocks
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(6)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();

        // GA processed first against the full, untouched calendar: adapts to
        // [2, 2] (2 blocks/2 days, 1 day margin - the same-hours tie is broken by
        // input order, GA first).
        List<Integer> gaLengths = saved.stream().filter(b -> b.getGroupId().equals("GA"))
                .map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(2, 2), gaLengths);

        // GB processed second sees only 1 day left (2h) - not enough for any
        // margin-safe or even bare-feasible longer shape, so it falls all the way
        // back to the untouched naive shape - a genuinely different, worse outcome
        // than GB would have gotten computed independently (it would also have
        // gotten [2, 2] against a fresh, full calendar).
        List<Integer> gbLengths = saved.stream().filter(b -> b.getGroupId().equals("GB"))
                .map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(1, 1, 1, 1), gbLengths);
    }

    @Test
    public void sharedTeacher_pairingsProcessedLargestHoursFirstRegardlessOfInputOrder() {
        // Same 3-day/2h-window teacher as above, but this time the SMALLER
        // pairing (2h) is added first and the LARGER one (4h) second - if
        // largest-hours-first is honored, the 4h pairing still gets first claim
        // on the calendar (and the favorable margin-safe shape), not the 2h one.
        StudentGroupEntity smallGroup = new StudentGroupEntity("SMALL", "Small Group");
        smallGroup.addCourse("Mathematics").setDefaultTeacherId("T1");
        StudentGroupEntity bigGroup = new StudentGroupEntity("BIG", "Big Group");
        bigGroup.addCourse("Physics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(smallGroup, bigGroup));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(courseRepository.findByName("Physics"))
                .thenReturn(Optional.of(course("C2", "Physics", 4, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId(anyString(), anyString())).thenReturn(false);

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        for (int day = 1; day <= 3; day++) {
            teacher.addAvailability(day, 7);
            teacher.addAvailability(day, 8);
        }
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        // BIG gets [2, 2] (2 blocks), SMALL gets [2] (1 block) - 3 total, not
        // one-per-hour, since both shapes above are already block counts, not
        // hour counts.
        assertEquals(3, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(3)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();

        // BIG (4h, added second) must still be processed first: [2, 2], the
        // margin-safe shape - proving the largest-hours-first ordering, not
        // input/iteration order, decided who got first claim on the calendar.
        List<Integer> bigLengths = saved.stream().filter(b -> b.getGroupId().equals("BIG"))
                .map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(2, 2), bigLengths);

        // SMALL (2h, added first) is left with the calendar's leftovers.
        List<Integer> smallLengths = saved.stream().filter(b -> b.getGroupId().equals("SMALL"))
                .map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(2), smallLengths);
    }

    @Test
    public void sharedTeacher_withAmpleAvailability_bothPairingsGetTheSameUnaffectedShape() {
        // A real school week only has 5 distinct days at all, so two 4h/BASICAS
        // pairings (the "tight" test above) can never both have true margin - by
        // definition, one pairing's 4-day consumption alone leaves at most 1 day
        // for anyone else. Genuine "ample, unaffected" slack instead needs a
        // smaller ask: two groups each needing 2h, from a teacher with 5 days -
        // even after the first group's 2 blocks consume 2 days, 3 remain, still
        // comfortably >= the second group's own 2-needed+1-margin = 3.
        StudentGroupEntity groupA = new StudentGroupEntity("GA", "Group A");
        groupA.addCourse("Mathematics").setDefaultTeacherId("T1");
        StudentGroupEntity groupB = new StudentGroupEntity("GB", "Group B");
        groupB.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(groupA, groupB));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId(anyString(), anyString())).thenReturn(false);

        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        for (int day = 1; day <= 5; day++) {
            teacher.addAvailability(day, 7); // BASICAS is 1h/block - 1h/day is enough
        }
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(4, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(4)).save(captor.capture());
        List<CourseBlockAssignmentEntity> saved = captor.getAllValues();

        // Both groups keep the naive [1, 1] BASICAS shape - neither one's margin
        // math is disturbed by the other's consumption, since 5 real days is
        // comfortably more than either pairing needs even after the first one
        // claims its share.
        for (CourseBlockAssignmentEntity block : saved) {
            assertEquals(Integer.valueOf(1), block.getBlockLength());
        }
    }

    @Test
    public void singleTeacherPairing_neverGetsAnUnnecessarySharedCalendar() {
        // A teacher appearing in only one pairing should behave exactly as
        // before this feature - no grouping, no ordering, no shared state.
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.addCourse("Mathematics").setDefaultTeacherId("T1");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));
        when(courseRepository.findByName("Mathematics"))
                .thenReturn(Optional.of(course("C1", "Mathematics", 2, "BASICAS", "estándar")));
        when(assignmentRepository.existsByGroupIdAndCourseId("G1", "C1")).thenReturn(false);
        when(assignmentRepository.findByTeacherId("T1")).thenReturn(List.of(new CourseBlockAssignmentEntity()));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.addAvailability(1, 7);
        teacher.addAvailability(2, 7);
        teacher.addAvailability(3, 7);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        BlockGenerationService.GenerationResult result = service.generateBlocks();

        assertEquals(2, result.getBlocksCreated());
        ArgumentCaptor<CourseBlockAssignmentEntity> captor = ArgumentCaptor.forClass(CourseBlockAssignmentEntity.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        List<Integer> lengths = captor.getAllValues().stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        assertEquals(List.of(1, 1), lengths); // naive shape, comfortable margin (2 needed + 1 <= 3 available)
    }

    @Test
    public void clearUnpinnedTimeslots_clearsOnlyUnpinnedRowsAndReturnsCount() {
        CourseBlockAssignmentEntity unpinned1 = new CourseBlockAssignmentEntity();
        unpinned1.setId("a1");
        unpinned1.setBlockTimeslotId("slot-1");
        unpinned1.setPinned(false);

        CourseBlockAssignmentEntity unpinned2 = new CourseBlockAssignmentEntity();
        unpinned2.setId("a2");
        unpinned2.setBlockTimeslotId("slot-2");
        unpinned2.setPinned(false);

        when(assignmentRepository.findByPinned(false)).thenReturn(List.of(unpinned1, unpinned2));

        int cleared = service.clearUnpinnedTimeslots();

        assertEquals(2, cleared);
        assertEquals(null, unpinned1.getBlockTimeslotId());
        assertEquals(null, unpinned2.getBlockTimeslotId());
        verify(assignmentRepository).saveAll(List.of(unpinned1, unpinned2));
    }
}
