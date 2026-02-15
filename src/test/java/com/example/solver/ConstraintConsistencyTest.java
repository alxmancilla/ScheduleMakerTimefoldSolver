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
                // Updated 2026-02-15: 10 HARD constraints (added "Non-standard rooms should
                // finish by 2pm")
                Set<String> expectedHardConstraints = new HashSet<>(Arrays.asList(
                                "Block length must match timeslot length",
                                "Teacher must be qualified",
                                "Teacher must be available for entire block",
                                "No teacher double-booking",
                                "No room double-booking",
                                "Room type must satisfy course requirement",
                                "Group cannot have two courses at same time",
                                "Non-standard rooms should finish by 2pm",
                                "Maximum 2 blocks per course per group per day"));

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
                // Updated 2026-02-15: 6 SOFT constraints (removed "Non-standard rooms should
                // finish by 2pm")
                Set<String> expectedSoftConstraints = new HashSet<>(Arrays.asList(
                                "Prefer course blocks to be consecutive on same day",
                                "Prefer group's preferred room",
                                "Minimize teacher building changes",
                                "Teacher exceeds max hours per week",
                                "Minimize teacher idle gaps (availability-aware)",
                                "Minimize group idle gaps",
                                "Prefer block's specified room"));

                // Assert all expected soft constraints are present
                for (String expected : expectedSoftConstraints) {
                        assertTrue("Analyzer missing SOFT constraint: " + expected,
                                        analyzerNames.contains(expected));
                }
        }

        /**
         * Test that "Non-standard rooms should finish by 2pm" is classified as HARD,
         * not SOFT.
         * This was reverted from SOFT to HARD on 2026-02-15.
         */
        @Test
        public void test2pmConstraintIsHard() {
                SchoolSchedule dummySchedule = createDummySchedule();

                // Check it IS in hard constraints
                Map<String, Integer> hardConstraints = BlockScheduleAnalyzer
                                .analyzeHardConstraintViolations(dummySchedule);
                assertTrue("2pm constraint should be in HARD constraints",
                                hardConstraints.containsKey("Non-standard rooms should finish by 2pm"));

                // Check it's NOT in soft constraints
                Map<String, Integer> softConstraints = BlockScheduleAnalyzer
                                .analyzeSoftConstraintViolations(dummySchedule);
                assertFalse("2pm constraint should NOT be in SOFT constraints",
                                softConstraints.containsKey("Non-standard rooms should finish by 2pm"));
        }

        /**
         * Test that the total number of constraints (HARD + SOFT) matches expectations.
         * This helps catch if a constraint is accidentally removed or added.
         * Updated 2026-02-15: 9 HARD + 7 SOFT = 16 total constraints.
         */
        @Test
        public void testTotalConstraintCount() {
                SchoolSchedule dummySchedule = createDummySchedule();

                int hardCount = BlockScheduleAnalyzer
                                .analyzeHardConstraintViolations(dummySchedule).size();
                int softCount = BlockScheduleAnalyzer
                                .analyzeSoftConstraintViolations(dummySchedule).size();

                // Expected counts (as of 2026-02-15)
                assertEquals("Expected 9 HARD constraints", 9, hardCount);
                assertEquals("Expected 7 SOFT constraints", 7, softCount);

                // Total should be 16 (9 hard + 7 soft)
                assertEquals("Total constraint count mismatch", 16, hardCount + softCount);
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
                Course course = new Course("1", "Test Course", "TEST", "II", "BASICAS",
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
