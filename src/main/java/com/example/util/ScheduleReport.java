package com.example.util;

import com.example.domain.SchoolSchedule;
import java.util.List;
import java.util.Map;

public class ScheduleReport {
    private final SchoolSchedule schedule;
    private final Map<String, Integer> hardViolations;
    private final Map<String, List<String>> hardViolationsDetailed;
    private final Map<String, Integer> softViolations;
    private final Map<String, List<String>> softViolationsDetailed;

    public ScheduleReport(SchoolSchedule schedule, Map<String, Integer> hardViolations,
            Map<String, List<String>> hardViolationsDetailed, Map<String, Integer> softViolations,
            Map<String, List<String>> softViolationsDetailed) {
        this.schedule = schedule;
        this.hardViolations = hardViolations;
        this.hardViolationsDetailed = hardViolationsDetailed;
        this.softViolations = softViolations;
        this.softViolationsDetailed = softViolationsDetailed;
    }

    public SchoolSchedule getSchedule() {
        return schedule;
    }

    public Map<String, Integer> getHardViolations() {
        return hardViolations;
    }

    public Map<String, List<String>> getHardViolationsDetailed() {
        return hardViolationsDetailed;
    }

    public Map<String, Integer> getSoftViolations() {
        return softViolations;
    }

    public Map<String, List<String>> getSoftViolationsDetailed() {
        return softViolationsDetailed;
    }
}