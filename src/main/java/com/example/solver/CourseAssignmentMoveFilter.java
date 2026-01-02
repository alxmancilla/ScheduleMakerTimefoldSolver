package com.example.solver;

import java.util.List;
import com.example.domain.Teacher;
import com.example.domain.Course;
import com.example.domain.CourseAssignment;
import com.example.domain.SchoolSchedule;
import com.example.util.CourseAssignmentValidator;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;

public class CourseAssignmentMoveFilter implements SelectionFilter<SchoolSchedule, CourseAssignment> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, CourseAssignment courseAssignment) {
        Teacher teacher = courseAssignment.getTeacher();
        Course course = courseAssignment.getCourse();
        List<CourseAssignment> assignments = scoreDirector.getWorkingSolution().getCourseAssignments();

        return CourseAssignmentValidator.canAssignCourse(teacher, course, assignments);
    }
}