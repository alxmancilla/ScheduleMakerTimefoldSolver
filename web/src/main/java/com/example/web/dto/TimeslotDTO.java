package com.example.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object for BlockTimeslot with validation. Mirrors the
 * database's CHECK constraints (check_block_day, check_block_start_hour,
 * check_block_length, check_block_end_hour) so invalid input is rejected with
 * a clean 400 instead of a raw DB error.
 */
public class TimeslotDTO {

    @NotNull(message = "Day of week is required")
    @Min(value = 1, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
    @Max(value = 7, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
    private Integer dayOfWeek;

    @NotNull(message = "Start hour is required")
    @Min(value = 7, message = "Start hour must be between 7 and 15")
    @Max(value = 15, message = "Start hour must be between 7 and 15")
    private Integer startHour;

    @NotNull(message = "Length in hours is required")
    @Min(value = 1, message = "Length must be between 1 and 4 hours")
    @Max(value = 4, message = "Length must be between 1 and 4 hours")
    private Integer lengthHours;

    public TimeslotDTO() {
    }

    @AssertTrue(message = "Start hour plus length must not exceed 15 (end of the school day)")
    public boolean isWithinDayBounds() {
        if (startHour == null || lengthHours == null) {
            return true;
        }
        return startHour + lengthHours <= 15;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Integer getStartHour() {
        return startHour;
    }

    public void setStartHour(Integer startHour) {
        this.startHour = startHour;
    }

    public Integer getLengthHours() {
        return lengthHours;
    }

    public void setLengthHours(Integer lengthHours) {
        this.lengthHours = lengthHours;
    }
}
