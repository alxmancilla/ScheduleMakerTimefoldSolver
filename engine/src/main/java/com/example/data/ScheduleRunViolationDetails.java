package com.example.data;

import com.example.analysis.ViolationInstance;
import java.util.List;
import java.util.Map;

/**
 * The detailed (per-offender, not just per-constraint counts) hard and soft
 * constraint violations for one solved schedule, exactly as
 * BlockScheduleAnalyzer.analyzeHardConstraintViolationsDetailed()/
 * .analyzeSoftConstraintViolationsDetailed() already compute them for the
 * console/PDF report - persisted onto schedule_run_violation (and, via each
 * ViolationInstance's assignmentIds, schedule_run_violation_assignment) at
 * save time (DataSaver) so admin/writer can see the same information in the
 * web Schedule view instead of only in a downloaded PDF, and so the view can
 * link a violation back to the specific grid card(s) it's about. Bundled
 * into one record rather than two more positional parameters on
 * {@link DataSaver#saveSchedule}, same rationale as {@link ScheduleRunMetadata}.
 *
 * @param hard constraint name -> one ViolationInstance per violation instance
 * @param soft constraint name -> one ViolationInstance per violation instance
 */
public record ScheduleRunViolationDetails(Map<String, List<ViolationInstance>> hard,
        Map<String, List<ViolationInstance>> soft) {
}
