package com.example.web.integration;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a room must SET NULL on every assignment that referenced it
 * (room_name, preferred_room_hint) rather than fail or silently leave a
 * dangling reference - real ON DELETE behavior a mocked repository test
 * cannot observe, since deleting the mock never touches the assignment at
 * all.
 */
class CourseBlockAssignmentCascadeIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void deletingARoomSetsNullOnReferencingAssignmentColumns() {
        roomRepository.save(new RoomEntity("R1", "EDIFICIO 1", "Standard"));
        studentGroupRepository.save(new StudentGroupEntity("G1", "Group 1"));
        CourseEntity course = new CourseEntity("C1", "Course 1", "Standard", 2);
        course.setAbbreviation("C1");
        course.setSemester(1);
        course.setDesignation("Core");
        courseRepository.save(course);

        CourseBlockAssignmentEntity assignment = new CourseBlockAssignmentEntity();
        assignment.setId("A1");
        assignment.setGroupId("G1");
        assignment.setCourseId("C1");
        assignment.setBlockLength(2);
        assignment.setRoomName("R1");
        assignment.setPreferredRoomHint("R1");
        assignmentRepository.save(assignment);
        testEntityManager.flush();

        roomRepository.deleteById("R1");
        testEntityManager.flush();
        testEntityManager.clear();

        CourseBlockAssignmentEntity reloaded = assignmentRepository.findById("A1").orElseThrow();
        assertThat(reloaded.getRoomName()).isNull();
        assertThat(reloaded.getPreferredRoomHint()).isNull();
    }
}
