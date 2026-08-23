package com.example.web.integration;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the bug fixed earlier this session: room.name FKs
 * defaulted to ON UPDATE NO ACTION, so renaming a room failed outright if
 * anything referenced it (database/migrations/add_room_rename_cascade.sql).
 * No mocked repository test could ever have caught this - it's purely a
 * question of what the real FK's ON UPDATE action does.
 */
class RoomRenameCascadeIT extends AbstractPostgresIntegrationTest {

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
    @Autowired
    private EntityManager entityManager;

    @Test
    void renamingARoomCascadesToEveryReferencingAssignmentColumn() {
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

        entityManager.createNativeQuery("UPDATE room SET name = 'R2' WHERE name = 'R1'").executeUpdate();
        testEntityManager.clear();

        CourseBlockAssignmentEntity reloaded = assignmentRepository.findById("A1").orElseThrow();
        assertThat(reloaded.getRoomName()).isEqualTo("R2");
        assertThat(reloaded.getPreferredRoomHint()).isEqualTo("R2");
    }
}
