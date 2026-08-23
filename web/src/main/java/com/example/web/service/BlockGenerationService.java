package com.example.web.service;

import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.entity.GroupCourseEntity;
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
import com.example.common.RoomTypeCompatibility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the unassigned course_block_assignment rows the solver actually
 * operates on, decomposing each (group, course) pair's hours into
 * block-length (1-4h) chunks. Without this step, a freshly imported problem
 * (Teachers/Courses/Rooms/Groups/Group_Courses only) has nothing for the
 * engine to solve, since DataLoader just reads existing
 * course_block_assignment rows rather than deriving them.
 *
 * Only fills gaps: a (group, course) pair that already has any block rows is
 * left untouched, so this is safe to re-run after adding a few new
 * group-course links without disturbing an already-solved schedule.
 *
 * Three sources feed a (group, course) pair's blocks, checked in this order:
 * 1. course_block_template rows (explicit, hand-authored blocks - a group's
 *    own template for a given blockIndex wins over a NULL-group/"applies to
 *    all groups" one for the same index). A template's preferredRoomName and
 *    preferredTimeslotId are pre-assigned onto the generated block directly
 *    (roomName, not just preferredRoomName), since room is never
 *    solver-assigned in this system - matching
 *    database/datasets/load_final_dataset_blocks.sql's generate_course_blocks().
 * 2. course_room_requirement rows (dual/multi room types, e.g. 4h in a
 *    computer center + 1h in a standard room): each requirement is decomposed
 *    separately, so the generated blocks carry the right
 *    satisfiesRoomType/preferredRoomName per portion.
 * 3. Neither: the single legacy CourseEntity.roomRequirement field for every
 *    block, as before dual requirements existed.
 *
 * Within tiers 2/3, each portion's hours are decomposed by greedily packing
 * component_block_rule's preferred block size for the course's component
 * (e.g. BASICAS=1 -> every block is 1h), falling back to DEFAULT_BLOCK_SIZE
 * for any component with no configured rule.
 *
 * Room defaulting: whenever a generated block would otherwise have no room
 * (no block-template preferredRoomName, no room-requirement
 * defaultPreferredRoom) and the group has a preferredRoomName whose type
 * satisfies the block's satisfiesRoomType - the same estándar/taller/mixto
 * compatibility convention as engine's Room.satisfiesRequirement() - the
 * group's preferred room is used as roomName directly, since room is never
 * solver-assigned. A group's preferred room never overrides a more specific
 * room already supplied by a template or room requirement.
 *
 * Teacher defaulting: every block generated for a (group, course) pair also
 * gets group_course.default_teacher_id (if set) as its teacher_id, since
 * teacher is likewise never solver-assigned. That column is the only place a
 * teacher can be pre-assigned before blocks exist (course_block_assignment
 * rows don't exist yet) - set via PUT
 * /api/groups/{groupId}/courses/{courseName}/default-teacher.
 */
@Service
public class BlockGenerationService {

    private static final int DEFAULT_BLOCK_SIZE = 2;

    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;
    @Autowired
    private CourseRoomRequirementRepository roomRequirementRepository;
    @Autowired
    private CourseBlockTemplateRepository blockTemplateRepository;
    @Autowired
    private ComponentBlockRuleRepository componentBlockRuleRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private TeacherRepository teacherRepository;

    @Transactional
    public GenerationResult generateBlocks() {
        int created = 0;
        int skippedExisting = 0;
        List<String> warnings = new ArrayList<>();

        for (StudentGroupEntity group : studentGroupRepository.findAll()) {
            for (GroupCourseEntity groupCourse : group.getCourses()) {
                CourseEntity course = courseRepository.findByName(groupCourse.getCourseName()).orElse(null);
                if (course == null) {
                    warnings.add("Group '" + group.getId() + "': course '" + groupCourse.getCourseName()
                            + "' not found, skipped");
                    continue;
                }
                if (assignmentRepository.existsByGroupIdAndCourseId(group.getId(), course.getId())) {
                    skippedExisting++;
                    continue;
                }
                created += generateBlocksForGroupCourse(group, course, groupCourse.getDefaultTeacherId());
            }
        }

        return new GenerationResult(created, skippedExisting, warnings);
    }

    /** Decomposes and saves the blocks for one (group, course) pair; returns how many blocks were created. */
    private int generateBlocksForGroupCourse(StudentGroupEntity group, CourseEntity course, String defaultTeacherId) {
        List<CourseBlockTemplateEntity> templates = resolveTemplates(course.getId(), group.getId());
        if (!templates.isEmpty()) {
            for (CourseBlockTemplateEntity template : templates) {
                saveTemplateBlock(group, course, template, defaultTeacherId);
            }
            return templates.size();
        }

        List<CourseRoomRequirementEntity> requirements = roomRequirementRepository.findByCourseIdOrderByPriority(course.getId());
        int blockIndex = 0;
        int created = 0;
        if (requirements.isEmpty()) {
            for (int length : decomposeHours(course.getRequiredHoursPerWeek(), course.getComponent())) {
                saveBlock(group, course, blockIndex, length, course.getRoomRequirement(), null, defaultTeacherId);
                blockIndex++;
                created++;
            }
        } else {
            for (CourseRoomRequirementEntity requirement : requirements) {
                for (int length : decomposeHours(requirement.getHoursRequired(), course.getComponent())) {
                    saveBlock(group, course, blockIndex, length, requirement.getRoomType(),
                            requirement.getDefaultPreferredRoom(), defaultTeacherId);
                    blockIndex++;
                    created++;
                }
            }
        }
        return created;
    }

    /**
     * Templates applicable to this (course, group), one per blockIndex: when
     * both a group-specific and a NULL-group ("all groups") template exist for
     * the same index - allowed by the DB's UNIQUE (course_id, group_id,
     * block_index), since group_id differs - the group-specific one wins as the
     * more targeted override.
     */
    private List<CourseBlockTemplateEntity> resolveTemplates(String courseId, String groupId) {
        Map<Integer, CourseBlockTemplateEntity> byIndex = new LinkedHashMap<>();
        for (CourseBlockTemplateEntity template : blockTemplateRepository.findApplicableTemplates(courseId, groupId)) {
            CourseBlockTemplateEntity existing = byIndex.get(template.getBlockIndex());
            if (existing == null || (existing.getGroupId() == null && template.getGroupId() != null)) {
                byIndex.put(template.getBlockIndex(), template);
            }
        }
        return byIndex.values().stream()
                .sorted((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()))
                .toList();
    }

    private void saveTemplateBlock(StudentGroupEntity group, CourseEntity course, CourseBlockTemplateEntity template,
            String defaultTeacherId) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(group.getId() + "_" + course.getId() + "_" + template.getBlockIndex());
        block.setGroupId(group.getId());
        block.setCourseId(course.getId());
        block.setBlockLength(template.getBlockLength());
        block.setSatisfiesRoomType(template.getRoomType());
        block.setPreferredRoomHint(template.getPreferredRoomName());
        block.setRoomName(template.getPreferredRoomName() != null
                ? template.getPreferredRoomName()
                : defaultRoomFor(group, template.getRoomType(), defaultTeacherId));
        block.setTeacherId(defaultTeacherId);
        block.setBlockTimeslotId(template.getPreferredTimeslotId());
        block.setPinned(Boolean.TRUE.equals(template.getPinAssignment()));
        assignmentRepository.save(block);
    }

    private void saveBlock(StudentGroupEntity group, CourseEntity course, int blockIndex, int length,
            String satisfiesRoomType, String preferredRoomHint, String defaultTeacherId) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(group.getId() + "_" + course.getId() + "_" + blockIndex);
        block.setGroupId(group.getId());
        block.setCourseId(course.getId());
        block.setBlockLength(length);
        block.setSatisfiesRoomType(satisfiesRoomType);
        block.setPreferredRoomHint(preferredRoomHint);
        block.setRoomName(preferredRoomHint != null ? preferredRoomHint
                : defaultRoomFor(group, satisfiesRoomType, defaultTeacherId));
        block.setTeacherId(defaultTeacherId);
        block.setPinned(false);
        assignmentRepository.save(block);
    }

    /**
     * The room a generated block should default to, in priority order: the
     * defaultTeacherId's required room (if set and type-compatible - a
     * teacher's fixed-room requirement overrides the group's preference), then
     * the group's preferred room (if set and type-compatible). Null if neither
     * applies, leaving the room for manual assignment instead of an invalid
     * default.
     */
    private String defaultRoomFor(StudentGroupEntity group, String satisfiesRoomType, String defaultTeacherId) {
        if (defaultTeacherId != null) {
            String teacherRoom = requiredRoomFor(defaultTeacherId, satisfiesRoomType);
            if (teacherRoom != null) {
                return teacherRoom;
            }
        }
        String preferredRoomName = group.getPreferredRoomName();
        if (preferredRoomName == null) {
            return null;
        }
        RoomEntity preferredRoom = roomRepository.findById(preferredRoomName).orElse(null);
        if (preferredRoom == null || !RoomTypeCompatibility.satisfies(preferredRoom.getType(), satisfiesRoomType)) {
            return null;
        }
        return preferredRoomName;
    }

    /** A teacher's required room, if set and its type satisfies the requirement - null otherwise. */
    private String requiredRoomFor(String teacherId, String satisfiesRoomType) {
        TeacherEntity teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null || teacher.getRequiredRoomName() == null) {
            return null;
        }
        RoomEntity requiredRoom = roomRepository.findById(teacher.getRequiredRoomName()).orElse(null);
        if (requiredRoom == null || !RoomTypeCompatibility.satisfies(requiredRoom.getType(), satisfiesRoomType)) {
            return null;
        }
        return teacher.getRequiredRoomName();
    }

    /**
     * Decomposes required hours by greedily packing blocks at the component's
     * configured preferred size (component_block_rule), or DEFAULT_BLOCK_SIZE
     * if that component has no rule, with a trailing remainder block for any
     * leftover hours that don't divide evenly.
     */
    private List<Integer> decomposeHours(int hours, String component) {
        int blockSize = componentBlockRuleRepository.findById(component)
                .map(ComponentBlockRuleEntity::getPreferredBlockSize)
                .orElse(DEFAULT_BLOCK_SIZE);

        List<Integer> lengths = new ArrayList<>();
        int remaining = hours;
        while (remaining > 0) {
            if (remaining >= blockSize) {
                lengths.add(blockSize);
                remaining -= blockSize;
            } else {
                lengths.add(remaining);
                remaining = 0;
            }
        }
        return lengths;
    }

    /** Outcome of a generateBlocks() run. */
    public static final class GenerationResult {
        private final int blocksCreated;
        private final int groupCoursesSkippedExisting;
        private final List<String> warnings;

        public GenerationResult(int blocksCreated, int groupCoursesSkippedExisting, List<String> warnings) {
            this.blocksCreated = blocksCreated;
            this.groupCoursesSkippedExisting = groupCoursesSkippedExisting;
            this.warnings = warnings;
        }

        public int getBlocksCreated() {
            return blocksCreated;
        }

        public int getGroupCoursesSkippedExisting() {
            return groupCoursesSkippedExisting;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
