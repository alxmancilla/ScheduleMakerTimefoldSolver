package com.example.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.example.domain.SchoolSchedule;

public class SchoolSolverConfig {

    public static SolverFactory<SchoolSchedule> buildSolverFactory() {
        try {
            return SolverFactory.createFromXmlResource("solverConfig.xml");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load solver configuration", e);
        }
    }
}
