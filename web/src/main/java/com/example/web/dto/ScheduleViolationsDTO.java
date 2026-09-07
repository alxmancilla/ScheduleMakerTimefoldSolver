package com.example.web.dto;

import com.example.web.entity.ScheduleRunViolationAssignmentEntity;
import com.example.web.entity.ScheduleRunViolationEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The persisted schedule_run_violation rows for one run (GET
 * /api/schedule/violations), pre-split into hard/soft so Schedule.jsx can
 * render two sections without re-filtering by isHard itself. {@code runId}
 * echoes back which run this actually resolved to - useful when the caller
 * passed no runId and got "the latest" instead. Each entry also carries the
 * assignment_id(s) it's about (schedule_run_violation_assignment), added
 * 2026-09-07, so the Schedule view can highlight/link the exact grid card(s)
 * a violation involves instead of only listing it as free text; a run
 * predating that link table (or this feature entirely) simply has an empty
 * list here, same "no rows yet" fallback as the rest of this DTO.
 */
public class ScheduleViolationsDTO {

    private final Integer runId;
    private final List<Entry> hard;
    private final List<Entry> soft;

    public ScheduleViolationsDTO(Integer runId, List<ScheduleRunViolationEntity> rows) {
        this(runId, rows, Collections.emptyList());
    }

    public ScheduleViolationsDTO(Integer runId, List<ScheduleRunViolationEntity> rows,
            List<ScheduleRunViolationAssignmentEntity> assignmentLinks) {
        this.runId = runId;
        Map<Long, List<String>> assignmentIdsByViolationId = assignmentLinks.stream()
                .collect(Collectors.groupingBy(ScheduleRunViolationAssignmentEntity::getViolationId,
                        Collectors.mapping(ScheduleRunViolationAssignmentEntity::getAssignmentId,
                                Collectors.toList())));
        this.hard = rows.stream().filter(r -> Boolean.TRUE.equals(r.getIsHard()))
                .map(r -> new Entry(r, assignmentIdsByViolationId.getOrDefault(r.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        this.soft = rows.stream().filter(r -> !Boolean.TRUE.equals(r.getIsHard()))
                .map(r -> new Entry(r, assignmentIdsByViolationId.getOrDefault(r.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    public Integer getRunId() {
        return runId;
    }

    public List<Entry> getHard() {
        return hard;
    }

    public List<Entry> getSoft() {
        return soft;
    }

    public static class Entry {
        private final String constraintName;
        private final String description;
        private final List<String> assignmentIds;

        public Entry(ScheduleRunViolationEntity entity, List<String> assignmentIds) {
            this.constraintName = entity.getConstraintName();
            this.description = entity.getDescription();
            this.assignmentIds = assignmentIds;
        }

        public String getConstraintName() {
            return constraintName;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getAssignmentIds() {
            return assignmentIds;
        }
    }
}
