// Shared across 2+ Settings tabs. Anything used by only one tab lives in
// that tab's own file instead - see each tab component's top for its
// tab-local constants (DAY_LABELS, BLOCK_SIZES, etc.).

export const ENGINE_POLL_MS = 3000;

export const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

// Used by both Timeslots (a block's own startHour) and Calendar (a HALF_DAY
// exception's endHour) - both select from the school's full operating range.
export const START_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15];

// SolverTab and ComplianceSnapshotsTab are independent, sibling tab
// components (each always mounted, shown/hidden via the `hidden` attribute -
// see Settings.jsx) with no shared parent state to coordinate through. A
// finished solver run may have written a new compliance PDF, so SolverTab
// dispatches this on the window (mirroring api.js's TERM_UPDATED_EVENT
// pattern) when its own status poll detects the run just finished;
// ComplianceSnapshotsTab listens for it to refresh its list automatically,
// the same way it did when both lived in one component.
export const ENGINE_RUN_FINISHED_EVENT = 'settings-engine-run-finished';
