package com.example.web.controller;

import com.example.web.entity.GroupCourseTeacherEntity;
import com.example.web.repository.GroupCourseTeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only course-coverage dashboard: for every (group, course) pair, how
 * many hours are actually scheduled against how many are required, and a
 * scheduling_status of "Complete" / "Partial" / "Not Scheduled" (see
 * GroupCourseTeacherEntity / the v_group_course_teachers view it's backed
 * by). Falls under the general GET rule (READER/WRITER/ADMIN, not TEACHER) -
 * same access level as the rest of the domain-data reporting views.
 */
@RestController
@RequestMapping("/api/course-coverage")
public class CourseCoverageController {

    @Autowired
    private GroupCourseTeacherRepository groupCourseTeacherRepository;

    @GetMapping
    public List<GroupCourseTeacherEntity> getCoverage() {
        return groupCourseTeacherRepository.findAll();
    }
}
