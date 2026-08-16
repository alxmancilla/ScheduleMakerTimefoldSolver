// The `room` table enforces a CHECK constraint restricting `type` (and, by
// convention, course_block_assignment.satisfies_room_type) to exactly these
// values. Keep in sync with database/schema_block_scheduling*.sql.
export const ROOM_TYPES = [
  'estándar',
  'laboratorio',
  'taller',
  'taller electromecánica',
  'taller electrónica',
  'centro de cómputo',
];
