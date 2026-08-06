package com.example;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.data.DataLoader;
import com.example.data.DataSaver;
import com.example.data.DemoDataGenerator;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

/**
 * End-to-end integration tests for the block-based scheduling pipeline.
 *
 * <p>
 * {@link #demoDataSolvesToInitializedSchedule()} always runs: it exercises the
 * generate -&gt; solve -&gt; score cycle on the in-memory demo dataset (no
 * database), proving the solver wiring, constraint provider and value-range
 * providers cooperate to produce a fully initialized, scored solution. It does
 * not assert feasibility because the rough demo dataset is not guaranteed to be
 * solvable to zero hard within a short budget.
 * </p>
 *
 * <p>
 * {@link #databaseLoadSolveSaveCycle()} performs the real load -&gt; solve
 * -&gt; save -&gt; verify cycle against the curated PostgreSQL dataset. It is
 * skipped unless connection parameters are supplied explicitly via the
 * {@code it.db.url} / {@code it.db.user} / {@code it.db.password} system
 * properties, so it never touches a database (or mutates data) by accident.
 * </p>
 */
public class BlockSchedulingIntegrationTest {

        /**
         * Build a solver whose termination stops at the first feasible solution
         * (0 hard) or after {@code maxSeconds}, whichever comes first.
         */
        private Solver<SchoolSchedule> buildBoundedSolver(long maxSeconds) {
                SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
                config.withTerminationConfig(new TerminationConfig()
                                .withBestScoreLimit("0hard/-2147483648soft")
                                .withSpentLimit(Duration.ofSeconds(maxSeconds)));
                return SolverFactory.<SchoolSchedule>create(config).buildSolver();
        }

        @Test
        public void demoDataSolvesToInitializedSchedule() {
                SchoolSchedule problem = DemoDataGenerator.generateBlockDemoData();
                assertNotNull("Demo schedule should not be null", problem);
                assertTrue("Demo schedule should contain block assignments",
                                problem.getCourseBlockAssignments().size() > 0);

                // Short budget: the construction heuristic fully initializes the solution
                // in well under a second; local search then briefly improves it.
                SchoolSchedule solved = buildBoundedSolver(10).solve(problem);

                assertNotNull("Solved schedule should not be null", solved);
                assertNotNull("Solved schedule should have a calculated score", solved.getScore());

                for (CourseBlockAssignment a : solved.getCourseBlockAssignments()) {
                        // Every planning entity must be fully initialized (timeslot assigned)...
                        assertNotNull("Assignment " + a.getId() + " must have a timeslot assigned",
                                        a.getTimeslot());
                        // ...and the core data-integrity rule must hold: block length matches
                        // the assigned timeslot length.
                        assertEquals("Block length must match timeslot length for " + a.getId(),
                                        (long) a.getBlockLength(), (long) a.getTimeslot().getLengthHours());
                }
        }

        /**
         * Regression guard: the analyzer's total hard-violation count must equal the
         * solver's hard score on the demo dataset. This protects against the class of
         * drift where the solver and analyzer disagree on hard constraints -- notably
         * the null-teacher / null-room phantom double-booking bug, where
         * {@code Joiners.equal} joined {@code null == null} and the solver's filters
         * lacked a null-guard, inflating the hard score far above the analyzer's count.
         *
         * <p>
         * The 2pm rule is excluded from the sum because it is intentionally classified
         * HARD in the analyzer but SOFT (weight 10) in the solver, so it must not
         * participate in a hard-score comparison.
         * </p>
         */
        @Test
        public void analyzerHardTotalMatchesSolverHardScoreOnDemoData() {
                SchoolSchedule solved = buildBoundedSolver(10).solve(DemoDataGenerator.generateBlockDemoData());

                Map<String, Integer> hard = BlockScheduleAnalyzer.analyzeHardConstraintViolations(solved);
                int analyzerHardTotal = hard.entrySet().stream()
                                .filter(e -> !"Non-standard rooms should finish by 2pm".equals(e.getKey()))
                                .mapToInt(Map.Entry::getValue)
                                .sum();

                assertEquals("Analyzer hard total must equal the solver's hard score. Breakdown: " + hard,
                                -solved.getScore().hardScore(), analyzerHardTotal);
        }

        @Test
        public void databaseLoadSolveSaveCycle() throws Exception {
                String url = System.getProperty("it.db.url");
                assumeTrue("Skipping DB integration test: set -Dit.db.url to enable", url != null);
                String user = System.getProperty("it.db.user", "");
                String password = System.getProperty("it.db.password", "");

                // Verify the database is reachable before proceeding; otherwise skip.
                try (Connection ignored = DriverManager.getConnection(url, user, password)) {
                        // reachable
                } catch (Exception e) {
                        assumeNoException("Skipping DB integration test: database not reachable", e);
                }

                // LOAD the curated dataset.
                SchoolSchedule problem = new DataLoader(url, user, password).loadDataForBlockScheduling();
                assertNotNull("Loaded schedule should not be null", problem);
                assertTrue("Loaded schedule should contain block assignments",
                                problem.getCourseBlockAssignments().size() > 0);

                // SOLVE with a bounded budget; the curated dataset is expected to be feasible.
                SchoolSchedule solved = buildBoundedSolver(120).solve(problem);
                assertNotNull("Solved schedule should have a score", solved.getScore());
                for (CourseBlockAssignment a : solved.getCourseBlockAssignments()) {
                        assertNotNull("Assignment " + a.getId() + " must have a timeslot", a.getTimeslot());
                }
                assertEquals("Curated dataset should solve to a feasible (0 hard) schedule",
                                0, solved.getScore().hardScore());

                // SAVE the solved timeslot assignments back to the database.
                DataSaver saver = new DataSaver(url, user, password);
                saver.saveSchedule(solved);

                // VERIFY: the persisted schedule reports assigned blocks (teacher, room
                // and timeslot all set).
                Map<String, Integer> stats = saver.getBlockScheduleStatistics();
                assertNotNull("Statistics should not be null", stats);
                assertTrue("At least one block should be persisted as assigned",
                                stats.getOrDefault("assigned_block_assignments", 0) > 0);
        }
}
