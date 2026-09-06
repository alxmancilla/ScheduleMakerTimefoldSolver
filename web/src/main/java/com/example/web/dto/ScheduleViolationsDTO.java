package com.example.web.dto;

import com.example.web.entity.ScheduleRunViolationEntity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The persisted schedule_run_violation rows for one run (GET
 * /api/schedule/violations), pre-split into hard/soft so Schedule.jsx can
 * render two sections without re-filtering by isHard itself. {@code runId}
 * echoes back which run this actually resolved to - useful when the caller
 * passed no runId and got "the latest" instead.
 */
public class ScheduleViolationsDTO {

    private final Integer runId;
    private final List<Entry> hard;
    private final List<Entry> soft;

    public ScheduleViolationsDTO(Integer runId, List<ScheduleRunViolationEntity> rows) {
        this.runId = runId;
        this.hard = rows.stream().filter(r -> Boolean.TRUE.equals(r.getIsHard())).map(Entry::new)
                .collect(Collectors.toList());
        this.soft = rows.stream().filter(r -> !Boolean.TRUE.equals(r.getIsHard())).map(Entry::new)
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

        public Entry(ScheduleRunViolationEntity entity) {
            this.constraintName = entity.getConstraintName();
            this.description = entity.getDescription();
        }

        public String getConstraintName() {
            return constraintName;
        }

        public String getDescription() {
            return description;
        }
    }
}
