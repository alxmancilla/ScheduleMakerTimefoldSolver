package com.example.web.integration;

import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.StudentGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CourseBlockTemplateRepository.findApplicableTemplates relies on Spring
 * Data JPA translating a null :groupId parameter into "group_id IS NULL" so
 * that "t.groupId = :groupId OR t.groupId IS NULL" still matches wildcard
 * rows when a real, non-null group is queried - documented as subtle in the
 * repository's own doc comment. A mocked repository can't validate that
 * Spring actually does this translation; only a real query against Postgres
 * can.
 */
class CourseBlockTemplateRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private CourseBlockTemplateRepository templateRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void wildcardAndGroupSpecificTemplatesBothApplyButOtherGroupsTemplatesDoNot() {
        CourseEntity course = new CourseEntity("C1", "Course 1", "Standard", 4);
        course.setAbbreviation("C1");
        course.setSemester(1);
        course.setDesignation("Core");
        courseRepository.save(course);
        studentGroupRepository.save(new StudentGroupEntity("G1", "Group 1"));
        studentGroupRepository.save(new StudentGroupEntity("G2", "Group 2"));

        CourseBlockTemplateEntity wildcard = new CourseBlockTemplateEntity(
                "C1", null, 1, 2, "Standard", null, null, false, null);
        CourseBlockTemplateEntity forG1 = new CourseBlockTemplateEntity(
                "C1", "G1", 2, 2, "Standard", null, null, false, null);
        CourseBlockTemplateEntity forG2 = new CourseBlockTemplateEntity(
                "C1", "G2", 3, 2, "Standard", null, null, false, null);
        templateRepository.save(wildcard);
        templateRepository.save(forG1);
        templateRepository.save(forG2);
        testEntityManager.flush();

        List<CourseBlockTemplateEntity> applicable = templateRepository.findApplicableTemplates("C1", "G1");

        assertThat(applicable).extracting(CourseBlockTemplateEntity::getBlockIndex).containsExactly(1, 2);
    }
}
