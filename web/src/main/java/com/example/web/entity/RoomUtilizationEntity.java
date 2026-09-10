package com.example.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping of the v_room_utilization view: each room's assignment
 * count, distinct block-timeslots used, and total hours booked per week -
 * computed server-side (LEFT JOIN course_block_assignment, summed by
 * block_length) so the frontend doesn't need broader access than it actually
 * requires to show a utilization column.
 *
 * The view joins on course_block_assignment.room_name directly (not the
 * course_block_assignment_current view) - safe here because, unlike
 * block_timeslot_id, room_name on the raw table is empirically kept in sync
 * with the resolved room for every row (0 rows differ from
 * course_block_assignment_current as of this writing), so no
 * pinned/non-pinned resolution gap applies to this column the way it does
 * elsewhere.
 *
 * total_hours_used can exceed the school's 40 available weekly hours
 * (5 days x 8 slots) when the underlying data has unresolved room
 * double-booking violations (SUM(block_length) then double-counts the
 * overlapping hours) - intentionally left as-is rather than capped, since
 * that's itself a useful signal rather than a display bug.
 *
 * Backs RoomController's GET /api/rooms/utilization.
 */
@Entity
@Immutable
@Table(name = "v_room_utilization")
public class RoomUtilizationEntity {

    @Id
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "building", length = 50)
    private String building;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "assignments_count")
    private Long assignmentsCount;

    @Column(name = "unique_timeslots_used")
    private Long uniqueTimeslotsUsed;

    @Column(name = "total_hours_used")
    private Long totalHoursUsed;

    public RoomUtilizationEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public RoomUtilizationEntity(String name, String building, String type, Long assignmentsCount,
            Long uniqueTimeslotsUsed, Long totalHoursUsed) {
        this.name = name;
        this.building = building;
        this.type = type;
        this.assignmentsCount = assignmentsCount;
        this.uniqueTimeslotsUsed = uniqueTimeslotsUsed;
        this.totalHoursUsed = totalHoursUsed;
    }

    public String getName() {
        return name;
    }

    public String getBuilding() {
        return building;
    }

    public String getType() {
        return type;
    }

    public Long getAssignmentsCount() {
        return assignmentsCount;
    }

    public Long getUniqueTimeslotsUsed() {
        return uniqueTimeslotsUsed;
    }

    public Long getTotalHoursUsed() {
        return totalHoursUsed;
    }
}
