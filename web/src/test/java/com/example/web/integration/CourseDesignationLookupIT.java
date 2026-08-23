package com.example.web.integration;

import com.example.web.entity.CourseEntity;
import com.example.web.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Before this session, course.designation (then named course.component) had
 * no constraint at all - a typo silently created a new, disconnected
 * designation with no block rule and no way to catch the mistake. Proves the
 * course_designation lookup table (database/migrations/
 * add_room_type_and_course_component_lookup_tables.sql,
 * rename_course_component_to_designation.sql) now makes that fail loudly
 * instead.
 */
class CourseDesignationLookupIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void courseWithADesignationNotInTheLookupTableIsRejected() {
        CourseEntity course = new CourseEntity("C1", "Course 1", "Standard", 2);
        course.setAbbreviation("C1");
        course.setSemester(1);
        course.setDesignation("TYPO_DESIGNATION");
        courseRepository.save(course);

        assertThrows(DataIntegrityViolationException.class, testEntityManager::flush);
    }

    @Test
    void courseWithAValidLookupDesignationSucceeds() {
        CourseEntity course = new CourseEntity("C1", "Course 1", "Standard", 2);
        course.setAbbreviation("C1");
        course.setSemester(1);
        course.setDesignation("Core");
        CourseEntity saved = courseRepository.save(course);
        testEntityManager.flush();

        assertThat(saved.getId()).isEqualTo("C1");
    }
}
