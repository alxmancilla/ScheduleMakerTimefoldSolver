package com.example.web.service;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the unassigned course_block_assignment rows the solver actually
 * operates on, decomposing each (group, course) pair's required_hours_per_week
 * into block-length (1-4h) chunks. Without this step, a freshly imported
 * problem (Teachers/Courses/Rooms/Groups/Group_Courses only) has nothing for
 * the engine to solve, since DataLoader just reads existing
 * course_block_assignment rows rather than deriving them.
 *
 * Only fills gaps: a (group, course) pair that already has any block rows is
 * left untouched, so this is safe to re-run after adding a few new
 * group-course links without disturbing an already-solved schedule.
 *
 * Mirrors the decomposition rules from database/datasets/load_final_dataset_blocks.sql's
 * generate_course_blocks() PL/pgSQL function, minus its course_block_template
 * special-casing (that table has no committed schema in this repo).
 */
@Service
public class BlockGenerationService {

    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

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
                int blockIndex = 0;
                for (int length : decomposeHours(course.getRequiredHoursPerWeek(), course.getComponent())) {
                    CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
                    block.setId(group.getId() + "_" + course.getId() + "_" + blockIndex);
                    block.setGroupId(group.getId());
                    block.setCourseId(course.getId());
                    block.setBlockLength(length);
                    block.setSatisfiesRoomType(course.getRoomRequirement());
                    block.setPinned(false);
                    assignmentRepository.save(block);
                    blockIndex++;
                    created++;
                }
            }
        }

        return new GenerationResult(created, skippedExisting, warnings);
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
