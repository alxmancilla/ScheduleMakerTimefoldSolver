// The `room_type` lookup table restricts `room.type` (and, by convention,
// course_block_assignment.satisfies_room_type) to exactly these values via
// FK. Keep in sync with database/schema_block_scheduling*.sql. Specialized -
// Workshop and Specialized - Computer Lab share a label prefix but stay
// non-interchangeable - see common/RoomTypeCompatibility.java.
export const ROOM_TYPES = [
  'Standard',
  'Mixed',
  'Specialized - Workshop',
  'Specialized - Computer Lab',
];

/** Zero-padded HH:00, e.g. formatHour(7) -> "07:00", formatHour(14) -> "14:00". */
export const formatHour = (hour) => `${String(hour).padStart(2, '0')}:00`;
