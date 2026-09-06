package com.example.data;

import java.util.List;
import java.util.Map;

/**
 * The detailed (per-offender, not just per-constraint counts) hard and soft
 * constraint violations for one solved schedule, exactly as
 * BlockScheduleAnalyzer.analyzeHardConstraintViolationsDetailed()/
 * .analyzeSoftConstraintViolationsDetailed() already compute them for the
 * console/PDF report - persisted onto schedule_run_violation at save time
 * (DataSaver) so admin/writer can see the same information in the web
 * Schedule view instead of only in a downloaded PDF. Bundled into one record
 * rather than two more positional parameters on
 * {@link DataSaver#saveSchedule}, same rationale as {@link ScheduleRunMetadata}.
 *
 * @param hard constraint name -> one description string per violation instance
 * @param soft constraint name -> one description string per violation instance
 */
public record ScheduleRunViolationDetails(Map<String, List<String>> hard, Map<String, List<String>> soft) {
}
