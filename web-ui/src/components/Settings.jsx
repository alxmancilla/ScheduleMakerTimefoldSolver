import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import TermTab from './settings/TermTab';
import SolverTab from './settings/SolverTab';
import ComplianceSnapshotsTab from './settings/ComplianceSnapshotsTab';
import GenerateBlocksTab from './settings/GenerateBlocksTab';
import BlockRulesTab from './settings/BlockRulesTab';
import ConstraintWeightsTab from './settings/ConstraintWeightsTab';
import SemesterHourLimitsTab from './settings/SemesterHourLimitsTab';
import CalendarTab from './settings/CalendarTab';
import TimeslotsTab from './settings/TimeslotsTab';
import DatabaseBackupsTab from './settings/DatabaseBackupsTab';
import AuditLogTab from './settings/AuditLogTab';

// Each of the 11 tabs below is a fully self-contained component (owns its
// own state, data loading, and handlers - see components/settings/) rather
// than one shared 1800-line component switching on activeTab. All visible
// tabs (see schedulerVisible below) are always mounted and shown/hidden via
// the `hidden` attribute, never conditionally rendered, so every tab's data
// loads once up front exactly like the original single-component version
// did - switching tabs never re-fetches. The only cross-tab coupling (a
// finished solver run refreshing the compliance-snapshots list) goes
// through a window event instead of shared state - see
// settings/constants.js's ENGINE_RUN_FINISHED_EVENT.
//
// schedulerVisible marks the two tabs SCHEDULER can also reach (this whole
// page is SchedulerRoute-gated - SCHEDULER/ADMIN - see App.jsx): triggering/
// configuring the solver, and constraint weights, matching SecurityConfig's
// own /api/admin/engine/**+/api/admin/constraint-config/** carve-out.
// Every other tab stays ADMIN-only within the page, same as its backing
// endpoint (term, compliance snapshots, generate blocks, block rules,
// semester hour limits, calendar, timeslots, database backups, audit log).
const SETTINGS_TABS = [
  { key: 'term', labelKey: 'settings.term.title', Component: TermTab },
  { key: 'solver', labelKey: 'settings.solver.title', Component: SolverTab, schedulerVisible: true },
  { key: 'complianceSnapshots', labelKey: 'settings.complianceSnapshots.title', Component: ComplianceSnapshotsTab },
  { key: 'generateBlocks', labelKey: 'settings.generateBlocks.title', Component: GenerateBlocksTab },
  { key: 'blockRules', labelKey: 'settings.blockRules.title', Component: BlockRulesTab },
  {
    key: 'constraintWeights', labelKey: 'settings.constraintWeights.title', Component: ConstraintWeightsTab,
    schedulerVisible: true,
  },
  { key: 'semesterHourLimits', labelKey: 'settings.semesterHourLimits.title', Component: SemesterHourLimitsTab },
  { key: 'calendar', labelKey: 'settings.calendar.title', Component: CalendarTab },
  { key: 'timeslots', labelKey: 'settings.timeslots.title', Component: TimeslotsTab },
  { key: 'databaseBackups', labelKey: 'settings.databaseBackups.title', Component: DatabaseBackupsTab },
  { key: 'auditLog', labelKey: 'settings.auditLog.title', Component: AuditLogTab },
];

function Settings() {
  const { t } = useTranslation();
  const { isAdmin } = useAuth();
  const admin = isAdmin();
  const visibleTabs = SETTINGS_TABS.filter((tab) => admin || tab.schedulerVisible);
  const [activeTab, setActiveTab] = useState(visibleTabs[0].key);

  return (
    <div>
      <div className="card">
        <h2>{t('settings.title')}</h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>{t('settings.description')}</p>
      </div>

      <div className="tabs" role="tablist">
        {visibleTabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            id={`settings-tab-${tab.key}`}
            role="tab"
            aria-selected={activeTab === tab.key}
            aria-controls={`settings-panel-${tab.key}`}
            className={`tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {visibleTabs.map((tab) => (
        <tab.Component key={tab.key} hidden={activeTab !== tab.key} />
      ))}
    </div>
  );
}

export default Settings;
