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
 * Before this session, course.component had no constraint at all - a typo
 * silently created a new, disconnected component with no block rule and no
 * way to catch the mistake. Proves the new course_component lookup table
 * (database/migrations/add_room_type_and_course_component_lookup_tables.sql)
 * now makes that fail loudly instead.
 */
class CourseComponentLookupIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void courseWithAComponentNotInTheLookupTableIsRejected() {
        CourseEntity course = new CourseEntity("C1", "Course 1", "estándar", 2);
        course.setAbbreviation("C1");
        course.setSemester(1);
        course.setComponent("TYPO_COMPONENT");
        courseRepository.save(course);

        assertThrows(DataIntegrityViolationException.class, testEntityManager::flush);
    }

    @Test
    void courseWithAValidLookupComponentSucceeds() {
        CourseEntity course = new CourseEntity("C1", "Course 1", "estándar", 2);
        course.setAbbreviation("C1");
        course.setSemester(1);
        course.setComponent("BASICAS");
        CourseEntity saved = courseRepository.save(course);
        testEntityManager.flush();

        assertThat(saved.getId()).isEqualTo("C1");
    }
}
