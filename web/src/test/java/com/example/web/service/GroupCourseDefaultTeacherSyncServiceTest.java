package com.example.web.service;

import com.example.web.entity.CourseEntity;
import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link GroupCourseDefaultTeacherSyncService} keeps
 * group_course.default_teacher_id aligned with whatever teacher a
 * course_block_assignment write actually used, without ever creating a new
 * group_course link as a side effect.
 */
@RunWith(MockitoJUnitRunner.class)
public class GroupCourseDefaultTeacherSyncServiceTest {

    @Mock
    private StudentGroupRepository groupRepository;
    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private GroupCourseDefaultTeacherSyncService service;

    @Test
    public void teacherChanged_updatesDefaultTeacherIdAndSavesGroup() {
        CourseEntity course = new CourseEntity("C1", "Math", "estándar", 4);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        GroupCourseEntity groupCourse = new GroupCourseEntity("G1", "Math");
        groupCourse.setDefaultTeacherId("OLD-TEACHER");
        group.getCourses().add(groupCourse);
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));

        service.sync("G1", "C1", "NEW-TEACHER");

        assertEquals("NEW-TEACHER", groupCourse.getDefaultTeacherId());
        verify(groupRepository).save(group);
    }

    @Test
    public void teacherAlreadyMatches_doesNotResave() {
        CourseEntity course = new CourseEntity("C1", "Math", "estándar", 4);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        GroupCourseEntity groupCourse = new GroupCourseEntity("G1", "Math");
        groupCourse.setDefaultTeacherId("T1");
        group.getCourses().add(groupCourse);
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));

        service.sync("G1", "C1", "T1");

        verify(groupRepository, never()).save(any());
    }

    @Test
    public void noGroupCourseLinkForThatPair_doesNotCreateOne() {
        CourseEntity course = new CourseEntity("C1", "Math", "estándar", 4);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        // group has no "Math" GroupCourseEntity at all (e.g. legacy/imported data
        // never linked through GroupCourseController.addCourse)
        when(groupRepository.findById("G1")).thenReturn(Optional.of(group));

        service.sync("G1", "C1", "T1");

        verify(groupRepository, never()).save(any());
    }

    @Test
    public void unknownGroup_doesNothing() {
        CourseEntity course = new CourseEntity("C1", "Math", "estándar", 4);
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        when(groupRepository.findById("NOPE")).thenReturn(Optional.empty());

        service.sync("NOPE", "C1", "T1");

        verify(groupRepository, never()).save(any());
    }

    @Test
    public void unknownCourse_doesNothing() {
        when(courseRepository.findById("NOPE")).thenReturn(Optional.empty());

        service.sync("G1", "NOPE", "T1");

        verify(groupRepository, never()).findById(any());
    }

    @Test
    public void nullTeacherId_isNoOp() {
        service.sync("G1", "C1", null);

        verifyNoInteractions(groupRepository, courseRepository);
    }

    @Test
    public void nullGroupId_isNoOp() {
        service.sync(null, "C1", "T1");

        verifyNoInteractions(groupRepository, courseRepository);
    }

    @Test
    public void nullCourseId_isNoOp() {
        service.sync("G1", null, "T1");

        verifyNoInteractions(groupRepository, courseRepository);
    }
}
