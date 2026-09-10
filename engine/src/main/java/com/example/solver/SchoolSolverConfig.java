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
        return build(minutesSpentLimit, unimprovedMinutesSpentLimit, null).factory();
    }

    /**
     * Same as {@link #build(Long, Long, Long)} with no randomSeed override -
     * solverConfig.xml's own fixed (reproducible) default is used unchanged.
     */
    public static Built build(Long minutesSpentLimit, Long unimprovedMinutesSpentLimit) {
        return build(minutesSpentLimit, unimprovedMinutesSpentLimit, null);
    }

    /**
     * Same as {@link #buildSolverFactory(Long, Long)}, but also returns the
     * *effective* time budget, random seed, and environment mode actually
     * resolved (the override if given, otherwise solverConfig.xml's own
     * value/default) - so a caller that records a run (see DataSaver) can
     * persist what was really used, not just what was explicitly passed in.
     *
     * @param minutesSpentLimit           overrides <minutesSpentLimit> if non-null
     * @param unimprovedMinutesSpentLimit overrides <unimprovedMinutesSpentLimit> if non-null
     * @param randomSeed                  overrides <randomSeed> if non-null - solverConfig.xml
     *                                     defines neither <randomSeed> nor <environmentMode>, so
     *                                     Timefold's own default (a fixed seed, fully reproducible
     *                                     given identical starting data) applies when this is null.
     *                                     A non-null value here is "Option B": a genuinely different
     *                                     search each time it's supplied, while staying reproducible
     *                                     on demand later by supplying that exact same value again.
     */
    public static Built build(Long minutesSpentLimit, Long unimprovedMinutesSpentLimit, Long randomSeed) {
        try {
            SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
            TerminationConfig terminationConfig = resolveLocalSearchTermination(solverConfig);
            if (minutesSpentLimit != null) {
                terminationConfig.setMinutesSpentLimit(minutesSpentLimit);
            }
            if (unimprovedMinutesSpentLimit != null) {
                terminationConfig.setUnimprovedMinutesSpentLimit(unimprovedMinutesSpentLimit);
            }
            if (randomSeed != null) {
                solverConfig.setRandomSeed(randomSeed);
            }
            SolverFactory<SchoolSchedule> factory = SolverFactory.create(solverConfig);
            return new Built(factory, terminationConfig.getMinutesSpentLimit(),
                    terminationConfig.getUnimprovedMinutesSpentLimit(), solverConfig.getRandomSeed(),
                    solverConfig.determineEnvironmentMode().name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load solver configuration", e);
        }
    }

    private static TerminationConfig resolveLocalSearchTermination(SolverConfig solverConfig) {
        for (PhaseConfig<?> phaseConfig : solverConfig.getPhaseConfigList()) {
            if (!(phaseConfig instanceof LocalSearchPhaseConfig localSearchPhaseConfig)) {
                continue;
            }
            TerminationConfig terminationConfig = localSearchPhaseConfig.getTerminationConfig();
            if (terminationConfig == null) {
                terminationConfig = new TerminationConfig();
                localSearchPhaseConfig.setTerminationConfig(terminationConfig);
            }
            return terminationConfig;
        }
        throw new IllegalStateException(
                "solverConfig.xml has no <localSearch> phase to apply termination overrides to");
    }

    /**
     * The built solver factory plus the effective (override-or-XML-default)
     * time budget, random seed (null when solverConfig.xml's own fixed
     * default was used unchanged), and environment mode it resolved.
     */
    public record Built(SolverFactory<SchoolSchedule> factory, Long minutesSpentLimit,
            Long unimprovedMinutesSpentLimit, Long randomSeed, String environmentMode) {
    }
}
