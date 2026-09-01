package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Tests to ensure consistency between SchoolConstraintProvider and
 * BlockScheduleAnalyzer.
 *
 * These tests prevent drift where constraints are added/removed in one place
 * but not the other,
 * which would cause incorrect reporting of violations.
 */
public class ConstraintConsistencyTest {

        /**
         * Test that all HARD constraints reported by BlockScheduleAnalyzer
         * match the actual HARD constraints in SchoolConstraintProvider.
         *
         * This prevents the analyzer from reporting on constraints that don't exist,
         * or missing constraints that do exist.
         */
        @Test
        public void testHardConstraintNamesMatch() {
                // Get constraint names from analyzer (what it reports)
                SchoolSchedule dummySchedule = createDummySchedule();
                Map<String, Integer> analyzerHardConstraints = BlockScheduleAnalyzer
                                .analyzeHardConstraintViolations(dummySchedule);
                Set<String> analyzerNames = analyzerHardConstraints.keySet();

                // Expected HARD constraint names from SchoolConstraintProvider
                // These should match the constraint names in the provider exactly
                // Updated 2026-08-11: 9 HARD constraints
                // Note: "Non-standard rooms should finish by 2pm" is SOFT in the
                // constraint provider and is now reported as SOFT by the analyzer too
                // (see testSoftConstraintNamesMatch).
                // "Course blocks must be consecutive" is HARD in both.
                Set<String> expectedHardConstraints = new HashSet<>(Arrays.asList(
                                "Block length must match timeslot length",
                                "Teacher must be qualified",
                                "Teacher must be available for entire block",
                                "No teacher double-booking",
                                "No room double-booking",
                                "Room type must satisfy course requirement",
                                "Teacher's required room must be used",
                                "Semester hour limits must be respected (hard)",
                                "Group cannot have two courses at same time",
                                "Maximum blocks per course per group per day",
                                "Course blocks must be consecutive"
                                // "Teacher must have a break after consecutive hours" and
                                // "Group must have a break after consecutive hours" - both TEMP
                                // DISABLED 2026-08-24, see SchoolConstraintProvider. Re-add here
                                // when re-enabled.
                                ));

                // Assert they match
                assertEquals("Analyzer reports different HARD constraints than expected",
                                expectedHardConstraints, analyzerNames);
        }

        /**
         * Test that all SOFT constraints reported by BlockScheduleAnalyzer
         * exist and have the correct classification.
         */
        @Test
        public void testSoftConstraintNamesMatch() {
                // Get constraint names from analyzer (what it reports)
                SchoolSchedule dummySchedule = createDummySchedule();
                Map<String, Integer> analyzerSoftConstraints = BlockScheduleAnalyzer
                                .analyzeSoftConstraintViolations(dummySchedule);
                Set<String> analyzerNames = analyzerSoftConstraints.keySet();

                // Expected SOFT constraint names from SchoolConstraintProvider
                // Updated 2026-08-11: 7 SOFT constraints
                // Note: "Prefer course blocks to be consecutive on same day" removed -
                // implemented as HARD constraint instead.
                // "Non-standard rooms should finish by 2pm" is SOFT (weight 10) and is
                // now reported here rather than in the HARD map.
                Set<String> expectedSoftConstraints = new HashSet<>(Arrays.asList(
                                "Prefer group's preferred room",
                                // "Minimize teacher building changes" - TEMP DISABLED
                                // 2026-08-24, see SchoolConstraintProvider. Re-add here when
                                // re-enabled.
                                "Teacher exceeds max hours per week",
                                "Minimize teacher idle gaps (availability-aware)",
                                // "Minimize group idle gaps" - TEMP DISABLED 2026-08-24, see
                                // SchoolConstraintProvider (replaced for first-semester groups
                                // by "Minimize first-semester group idle gaps" below). Re-add
                                // here when re-enabled.
                                "Prefer block's specified room",
                                "Non-standard rooms should finish by 2pm",
                                "Room capacity should fit group size",
                                // "Prefer Core 1h blocks at the same time across days" - TEMP
                                // DISABLED 2026-08-26, see SchoolConstraintProvider. Re-add
                                // here when re-enabled.
                                "Prefer first-semester blocks to start early",
                                "Minimize first-semester group idle gaps",
                                "Semester hour limits should be respected (soft)"));

                // Assert all expected soft constraints are present
                for (String expected : expectedSoftConstraints) {
                        assertTrue("Analyzer missing SOFT constraint: " + expected,
                                        analyzerNames.contains(expected));
                }
        }

        /**
         * Test that "Non-standard rooms should finish by 2pm" is classified as SOFT,
         * consistent with SchoolConstraintProvider where it is penalized with
         * HardSoftScore.ofSoft(10). Aligned 2026-08-11.
         */
        @Test
        public void test2pmConstraintIsSoft() {
                SchoolSchedule dummySchedule = createDummySchedule();

                // Check it IS in soft constraints
                Map<String, Integer> softConstraints = BlockScheduleAnalyzer
                                .analyzeSoftConstraintViolations(dummySchedule);
                assertTrue("2pm constraint should be in SOFT constraints",
                                softConstraints.containsKey("Non-standard rooms should finish by 2pm"));

                // Check it's NOT in hard constraints
                Map<String, Integer> hardConstraints = BlockScheduleAnalyzer
                                .analyzeHardConstraintViolations(dummySchedule);
                assertFalse("2pm constraint should NOT be in HARD constraints",
                                hardConstraints.containsKey("Non-standard rooms should finish by 2pm"));
        }

        /**
         * Test that the total number of constraints (HARD + SOFT) matches expectations.
         * This helps catch if a constraint is accidentally removed or added.
         * Updated 2026-08-11: 9 HARD + 7 SOFT = 16 total constraints (the 2pm rule was
         * moved from the HARD map to the SOFT map to match the solver).
         */
        @Test
        public void testTotalConstraintCount() {
                SchoolSchedule dummySchedule = createDummySchedule();

                int hardCount = BlockScheduleAnalyzer
                                .analyzeHardConstraintViolations(dummySchedule).size();
                int softCount = BlockScheduleAnalyzer
                                .analyzeSoftConstraintViolations(dummySchedule).size();

                // Expected counts (as of 2026-08-24: "Teacher must have a break after
                // consecutive hours", "Group must have a break after consecutive hours",
                // "Minimize teacher building changes", and "Minimize group idle gaps"
                // all TEMP DISABLED - see SchoolConstraintProvider; "Prefer Core 1h
                // blocks at the same time across days", "Prefer first-semester blocks
                // to start early", and "Minimize first-semester group idle gaps" newly
                // added - was 12 hard / 8 soft / 20 total before. As of 2026-08-26:
                // "Prefer Core 1h blocks at the same time across days" also TEMP
                // DISABLED - see SchoolConstraintProvider - was 10 hard / 9 soft / 19
                // total before. Also as of 2026-08-27: added "First-semester blocks
                // must finish by 2pm" (HARD) - was 10 hard / 8 soft / 18 total before.
                // As of 2026-09-01: "First-semester blocks must finish by 2pm"
                // generalized into "Semester hour limits must be respected (hard)"
                // (still HARD, same count) plus a new SOFT counterpart "Semester hour
                // limits should be respected (soft)" (see semester_hour_limit /
                // Course.getLatestEndHourSeverity()) - was 11 hard / 8 soft / 19 total
                // before.
                assertEquals("Expected 11 HARD constraints", 11, hardCount);
                assertEquals("Expected 9 SOFT constraints", 9, softCount);

                // Total should be 20 (11 hard + 9 soft)
                assertEquals("Total constraint count mismatch", 20, hardCount + softCount);
        }

        /**
         * Helper method to create a minimal dummy schedule for testing.
         * This schedule has no assignments, so all violation counts will be 0,
         * but we can still check that the constraint names are present.
         */
        private SchoolSchedule createDummySchedule() {
                // Add minimal data
                Teacher teacher = new Teacher("TEST", "Test", "Teacher",
                                new HashSet<>(), new HashMap<>(), 40);
                Course course = new Course("1", "Test Course", "TEST", 2, "BASICAS",
                                "estándar", 4, Boolean.TRUE);
                Room room = new Room("ROOM1", "Building A", "estándar");
                Group group = new Group("TEST", "Test Group", new HashSet<>());
                BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

                // Use constructor to create schedule with all problem facts
                SchoolSchedule schedule = new SchoolSchedule(
                                Collections.singletonList(teacher),
                                Collections.singletonList(timeslot),
                                Collections.singletonList(room),
                                Collections.singletonList(course),
                                Collections.singletonList(group),
                                new ArrayList<>());

                return schedule;
        }
}
