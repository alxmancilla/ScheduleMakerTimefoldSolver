package com.example.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.example.domain.SchoolSchedule;
import java.io.File;

public class SchoolSolverConfig {

    public static SolverFactory<SchoolSchedule> buildSolverFactory() {
        try {
            File configFile = new File(SchoolSolverConfig.class.getClassLoader()
                    .getResource("solverConfig.xml").toURI());
            return SolverFactory.createFromXmlFile(configFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load solver configuration", e);
        }
    }
}
