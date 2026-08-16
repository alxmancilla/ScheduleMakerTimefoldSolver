package com.example.web.repository;

import com.example.web.entity.CourseBlockTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseBlockTemplateRepository extends JpaRepository<CourseBlockTemplateEntity, Long> {
    List<CourseBlockTemplateEntity> findByCourseIdOrderByGroupIdAscBlockIndexAsc(String courseId);

    /**
     * Templates that apply to this exact (course, group): either specific to
     * this group, or a wildcard (group_id IS NULL) applying to every group
     * taking the course. Spring Data JPA translates a null groupId parameter to
     * "group_id = groupId IS NULL" automatically, so passing a non-null groupId
     * here still matches wildcard rows via the explicit OR.
     */
    @Query("SELECT t FROM CourseBlockTemplateEntity t WHERE t.courseId = :courseId "
            + "AND (t.groupId = :groupId OR t.groupId IS NULL) ORDER BY t.blockIndex")
    List<CourseBlockTemplateEntity> findApplicableTemplates(@Param("courseId") String courseId, @Param("groupId") String groupId);

    boolean existsByCourseIdAndGroupIdAndBlockIndex(String courseId, String groupId, Integer blockIndex);

    boolean existsByCourseIdAndGroupIdAndBlockIndexAndIdNot(String courseId, String groupId, Integer blockIndex, Long id);
}
