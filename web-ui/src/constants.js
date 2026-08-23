// The `room` table enforces a CHECK constraint restricting `type` (and, by
// convention, course_block_assignment.satisfies_room_type) to exactly these
// values. Keep in sync with database/schema_block_scheduling*.sql.
export const ROOM_TYPES = [
  'estándar',
  'mixto',
  'taller',
  'centro de cómputo',
];

/** Zero-padded HH:00, e.g. formatHour(7) -> "07:00", formatHour(14) -> "14:00". */
export const formatHour = (hour) => `${String(hour).padStart(2, '0')}:00`;
