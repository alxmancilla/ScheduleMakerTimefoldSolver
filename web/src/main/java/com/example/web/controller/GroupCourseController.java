package com.example.web.controller;

import com.example.web.dto.AddGroupCourseRequest;
import com.example.web.dto.SetGroupCourseTeacherRequest;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/**
 * CRUD for which courses a group takes (group_course): previously only
 * settable via a full Excel re-import, with no way to view or edit it
 * directly. Nested under /api/groups/{groupId}/courses; falls under the
 * general /api/** GET/POST/DELETE role rules, no ADMIN-only restriction.
 *
 * group_course.course_name is the FK (not course_id), matching how
 * BlockGenerationService and ExcelImportService already resolve a course by
 * name for this table - so this controller works in course names too, not
 * IDs. Removing a link here does not touch any course_block_assignment rows
 * already generated for that (group, course) pair; it only affects future
 * "Generate Blocks" runs.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/courses")
public class GroupCourseController {

    @Autowired
    private StudentGroupRepository groupRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @GetMapping
    public List<GroupCourseEntity> getCourses(@PathVariable String groupId) {
        StudentGroupEntity group = requireGroup(groupId);
        return group.getCourses().stream()
                .sorted(Comparator.comparing(GroupCourseEntity::getCourseName))
                .toList();
    }

    @PostMapping
    public GroupCourseEntity addCourse(@PathVariable String groupId, @Valid @RequestBody AddGroupCourseRequest request) {
        StudentGroupEntity group = requireGroup(groupId);
        String courseName = request.getCourseName();
        CourseEntity course = courseRepository.findByName(courseName)
                .orElseThrow(() -> new IllegalArgumentException("Course '" + courseName + "' does not exist"));
        if (Boolean.FALSE.equals(course.getActive())) {
            throw new IllegalArgumentException("Course '" + courseName + "' is inactive and cannot be added to a group");
        }
        boolean alreadyLinked = group.getCourses().stream().anyMatch(gc -> gc.getCourseName().equals(courseName));
        if (alreadyLinked) {
            throw new IllegalArgumentException("This group already has course '" + courseName + "'");
        }
        group.addCourse(courseName);
        groupRepository.save(group);
        return group.getCourses().stream()
                .filter(gc -> gc.getCourseName().equals(courseName))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Pre-assigns (or clears, when teacherId is null) a teacher for this
     * (group, course) pairing before blocks exist. Applied by
     * BlockGenerationService to every block it creates for this pairing; has
     * no effect on this endpoint's own write once blocks already exist (that
     * pairing is then skipped by "Generate Blocks" and its blocks carry their
     * own teacher_id) - but default_teacher_id itself is still kept current
     * for such pairings via a different path: {@link
     * com.example.web.service.GroupCourseDefaultTeacherSyncService} writes it
     * automatically whenever an assignment's teacher is set through
     * CourseBlockAssignmentController or the Excel import, so a future
     * regeneration of that pairing's blocks still has an accurate teacher to
     * read.
     */
    @PutMapping("/{courseName}/default-teacher")
    public GroupCourseEntity setDefaultTeacher(@PathVariable String groupId, @PathVariable String courseName,
            @Valid @RequestBody SetGroupCourseTeacherRequest request) {
        StudentGroupEntity group = requireGroup(groupId);
        GroupCourseEntity groupCourse = group.getCourses().stream()
                .filter(gc -> gc.getCourseName().equals(courseName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Group course", groupId + "/" + courseName));

        String teacherId = request.getTeacherId();
        if (teacherId != null) {
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Teacher '" + teacherId + "' does not exist"));
            boolean qualified = teacher.getQualifications().stream()
                    .anyMatch(q -> q.getQualification().equals(courseName));
            if (!qualified) {
                throw new IllegalArgumentException(
                        "Teacher '" + teacherId + "' is not qualified for course '" + courseName + "'");
            }
        }

        groupCourse.setDefaultTeacherId(teacherId);
        groupRepository.save(group);
        return groupCourse;
    }

    @DeleteMapping("/{courseName}")
    public ResponseEntity<Void> removeCourse(@PathVariable String groupId, @PathVariable String courseName) {
        StudentGroupEntity group = requireGroup(groupId);
        GroupCourseEntity toRemove = group.getCourses().stream()
                .filter(gc -> gc.getCourseName().equals(courseName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Group course", groupId + "/" + courseName));
        group.getCourses().remove(toRemove);
        groupRepository.save(group);
        return ResponseEntity.noContent().build();
    }

    private StudentGroupEntity requireGroup(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
    }
}
