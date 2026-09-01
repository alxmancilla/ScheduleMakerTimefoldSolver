package com.example.common;

/**
 * The school week's real operating hours/days - shared by engine
 * (BlockScheduleMath.EARLIEST_START_HOUR delegates here) and web (the
 * semester-hour-limit guardrails: bounds validation and the capacity
 * warning, both of which need to reason about the same real window engine
 * schedules into). Confirmed against the live block_timeslot data: 5 days
 * (Monday-Friday), hours 7 through 15.
 */
public final class SchoolCalendarConstants {

    private SchoolCalendarConstants() {
    }

    public static final int EARLIEST_START_HOUR = 7;
    public static final int LATEST_HOUR = 15;
    public static final int SCHOOL_DAYS_PER_WEEK = 5;
}
