package com.example.web.service;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import com.example.web.repository.StudentGroupRepository;
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
 */
@Service
public class BlockGenerationService {

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
                created += generateBlocksForGroupCourse(group, course);
            }
        }

        return new GenerationResult(created, skippedExisting, warnings);
    }

    /** Decomposes and saves the blocks for one (group, course) pair; returns how many blocks were created. */
    private int generateBlocksForGroupCourse(StudentGroupEntity group, CourseEntity course) {
        List<CourseBlockTemplateEntity> templates = resolveTemplates(course.getId(), group.getId());
        if (!templates.isEmpty()) {
            for (CourseBlockTemplateEntity template : templates) {
                saveTemplateBlock(group, course, template);
            }
            return templates.size();
        }

        List<CourseRoomRequirementEntity> requirements = roomRequirementRepository.findByCourseIdOrderByPriority(course.getId());
        int blockIndex = 0;
        int created = 0;
        if (requirements.isEmpty()) {
            for (int length : decomposeHours(course.getRequiredHoursPerWeek(), course.getComponent())) {
                saveBlock(group, course, blockIndex, length, course.getRoomRequirement(), null);
                blockIndex++;
                created++;
            }
        } else {
            for (CourseRoomRequirementEntity requirement : requirements) {
                for (int length : decomposeHours(requirement.getHoursRequired(), course.getComponent())) {
                    saveBlock(group, course, blockIndex, length, requirement.getRoomType(),
                            requirement.getDefaultPreferredRoom());
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

    private void saveTemplateBlock(StudentGroupEntity group, CourseEntity course, CourseBlockTemplateEntity template) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(group.getId() + "_" + course.getId() + "_" + template.getBlockIndex());
        block.setGroupId(group.getId());
        block.setCourseId(course.getId());
        block.setBlockLength(template.getBlockLength());
        block.setSatisfiesRoomType(template.getRoomType());
        block.setPreferredRoomName(template.getPreferredRoomName());
        block.setRoomName(template.getPreferredRoomName());
        block.setBlockTimeslotId(template.getPreferredTimeslotId());
        block.setPinned(Boolean.TRUE.equals(template.getPinAssignment()));
        assignmentRepository.save(block);
    }

    private void saveBlock(StudentGroupEntity group, CourseEntity course, int blockIndex, int length,
            String satisfiesRoomType, String preferredRoomName) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(group.getId() + "_" + course.getId() + "_" + blockIndex);
        block.setGroupId(group.getId());
        block.setCourseId(course.getId());
        block.setBlockLength(length);
        block.setSatisfiesRoomType(satisfiesRoomType);
        block.setPreferredRoomName(preferredRoomName);
        block.setPinned(false);
        assignmentRepository.save(block);
    }

    /**
     * Decomposes required hours into 1-2 hour blocks. A BASICAS course with
     * exactly 2 hours/week becomes two separate 1-hour blocks (maximizes
     * scheduling flexibility for general-ed courses); everything else greedily
     * uses 2-hour blocks with a trailing 1-hour block for an odd remainder.
     */
    private List<Integer> decomposeHours(int hours, String component) {
        List<Integer> lengths = new ArrayList<>();
        if ("BASICAS".equals(component) && hours == 2) {
            lengths.add(1);
            lengths.add(1);
            return lengths;
        }
        int remaining = hours;
        while (remaining > 0) {
            if (remaining >= 2) {
                lengths.add(2);
                remaining -= 2;
            } else {
                lengths.add(1);
                remaining -= 1;
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
