package com.example.web.service;

import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Keeps group_course.default_teacher_id in sync with the teacher actually
 * being used for a (group, course) pairing, whenever a course_block_assignment
 * row's teacher is set through any write path - manual create/update
 * (CourseBlockAssignmentController) or Excel import (AssignmentExcelService).
 *
 * Without this, default_teacher_id silently drifts out of sync with reality
 * for any pairing whose blocks already exist: GroupCourseController's own
 * default-teacher endpoint has "no effect once blocks already exist" (see its
 * javadoc), so nothing else keeps this column current once a pairing has
 * blocks - exactly the pairings that matter if those blocks are ever
 * regenerated, since regeneration reads this column, not the blocks it's
 * about to replace. This service closes that gap at the point a teacher
 * assignment actually changes, rather than leaving default_teacher_id to be
 * discovered stale later.
 *
 * A no-op when the (group, course) pairing has no group_course row at all
 * (legacy/imported data never linked through GroupCourseController's
 * addCourse) - it only ever updates an existing link, never creates one as a
 * side effect of editing an assignment.
 */
@Service
public class GroupCourseDefaultTeacherSyncService {

    @Autowired
    private StudentGroupRepository groupRepository;

    @Autowired
    private CourseRepository courseRepository;

    /**
     * @param groupId   the assignment's group_id
     * @param courseId  the assignment's course_id (group_course keys off course
     *                  name, not id, so this is resolved via CourseRepository)
     * @param teacherId the assignment's teacher_id - a no-op if null, since an
     *                  unassigned/roomless-teacher block says nothing about who
     *                  the group's course teacher actually is
     */
    public void sync(String groupId, String courseId, String teacherId) {
        if (groupId == null || courseId == null || teacherId == null) {
            return;
        }
        courseRepository.findById(courseId).ifPresent(course -> {
            StudentGroupEntity group = groupRepository.findById(groupId).orElse(null);
            if (group == null) {
                return;
            }
            for (GroupCourseEntity groupCourse : group.getCourses()) {
                if (groupCourse.getCourseName().equals(course.getName())) {
                    if (!teacherId.equals(groupCourse.getDefaultTeacherId())) {
                        groupCourse.setDefaultTeacherId(teacherId);
                        groupRepository.save(group);
                    }
                    return;
                }
            }
        });
    }
}
