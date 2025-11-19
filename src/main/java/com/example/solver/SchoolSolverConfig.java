package com.example.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.example.domain.SchoolSchedule;

public class SchoolSolverConfig {

    public static SolverFactory<SchoolSchedule> buildSolverFactory() {
        return SolverFactory.create(new SolverConfig()
                .withEntityClasses(com.example.domain.CourseAssignment.class)
                .withSolutionClass(SchoolSchedule.class)
                .withConstraintProviderClass(SchoolConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(300L)));
    }
}
