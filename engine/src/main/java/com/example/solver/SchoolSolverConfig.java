package com.example.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.example.domain.SchoolSchedule;

public class SchoolSolverConfig {

    public static SolverFactory<SchoolSchedule> buildSolverFactory() {
        return buildSolverFactory(null, null);
    }

    /**
     * Builds the solver factory from solverConfig.xml, optionally overriding the
     * local search phase's total/unimproved time budget. Either argument may be
     * null to leave that XML value untouched - this is the only override point;
     * every other setting (acceptor, move selectors, bestScoreLimit, etc.) always
     * comes from the XML file, unchanged.
     *
     * @param minutesSpentLimit           overrides <minutesSpentLimit> if non-null
     * @param unimprovedMinutesSpentLimit overrides <unimprovedMinutesSpentLimit> if non-null
     */
    public static SolverFactory<SchoolSchedule> buildSolverFactory(Long minutesSpentLimit,
            Long unimprovedMinutesSpentLimit) {
        try {
            SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
            if (minutesSpentLimit != null || unimprovedMinutesSpentLimit != null) {
                applyTerminationOverrides(solverConfig, minutesSpentLimit, unimprovedMinutesSpentLimit);
            }
            return SolverFactory.create(solverConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load solver configuration", e);
        }
    }

    private static void applyTerminationOverrides(SolverConfig solverConfig, Long minutesSpentLimit,
            Long unimprovedMinutesSpentLimit) {
        for (PhaseConfig<?> phaseConfig : solverConfig.getPhaseConfigList()) {
            if (!(phaseConfig instanceof LocalSearchPhaseConfig localSearchPhaseConfig)) {
                continue;
            }
            TerminationConfig terminationConfig = localSearchPhaseConfig.getTerminationConfig();
            if (terminationConfig == null) {
                terminationConfig = new TerminationConfig();
                localSearchPhaseConfig.setTerminationConfig(terminationConfig);
            }
            if (minutesSpentLimit != null) {
                terminationConfig.setMinutesSpentLimit(minutesSpentLimit);
            }
            if (unimprovedMinutesSpentLimit != null) {
                terminationConfig.setUnimprovedMinutesSpentLimit(unimprovedMinutesSpentLimit);
            }
            return;
        }
        throw new IllegalStateException(
                "solverConfig.xml has no <localSearch> phase to apply termination overrides to");
    }
}
