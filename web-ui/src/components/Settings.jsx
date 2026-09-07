import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import TermTab from './settings/TermTab';
import ComplianceSnapshotsTab from './settings/ComplianceSnapshotsTab';
import GenerateBlocksTab from './settings/GenerateBlocksTab';
import BlockRulesTab from './settings/BlockRulesTab';
import SemesterHourLimitsTab from './settings/SemesterHourLimitsTab';
import CalendarTab from './settings/CalendarTab';
import TimeslotsTab from './settings/TimeslotsTab';
import DatabaseBackupsTab from './settings/DatabaseBackupsTab';
import AuditLogTab from './settings/AuditLogTab';

// This page is ADMIN-only (AdminRoute in App.jsx) - Solver and Constraint
// Weights moved out to their own SchedulerSettings.jsx page (added
// 2026-09-07, reached via a dedicated "Scheduler" nav item) so SCHEDULER
// never has to browse an "Admin" menu to reach what it actually has access
// to. Each of the 9 tabs below is a fully self-contained component (owns
// its own state, data loading, and handlers - see components/settings/)
// rather than one shared component switching on activeTab. All are always
// mounted and shown/hidden via the `hidden` attribute, never conditionally
// rendered, so every tab's data loads once up front - switching tabs never
// re-fetches. The only cross-tab coupling (a finished solver run - now
// triggered from SchedulerSettings.jsx - refreshing this page's compliance-
// snapshots list) goes through a window event instead of shared state - see
// settings/constants.js's ENGINE_RUN_FINISHED_EVENT.
const SETTINGS_TABS = [
  { key: 'term', labelKey: 'settings.term.title', Component: TermTab },
  { key: 'complianceSnapshots', labelKey: 'settings.complianceSnapshots.title', Component: ComplianceSnapshotsTab },
  { key: 'generateBlocks', labelKey: 'settings.generateBlocks.title', Component: GenerateBlocksTab },
  { key: 'blockRules', labelKey: 'settings.blockRules.title', Component: BlockRulesTab },
  { key: 'semesterHourLimits', labelKey: 'settings.semesterHourLimits.title', Component: SemesterHourLimitsTab },
  { key: 'calendar', labelKey: 'settings.calendar.title', Component: CalendarTab },
  { key: 'timeslots', labelKey: 'settings.timeslots.title', Component: TimeslotsTab },
  { key: 'databaseBackups', labelKey: 'settings.databaseBackups.title', Component: DatabaseBackupsTab },
  { key: 'auditLog', labelKey: 'settings.auditLog.title', Component: AuditLogTab },
];

function Settings() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('term');

  return (
    <div>
      <div className="card">
        <h2>{t('settings.title')}</h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>{t('settings.description')}</p>
      </div>

      <div className="tabs" role="tablist">
        {SETTINGS_TABS.map((tab) => (
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

      {SETTINGS_TABS.map((tab) => (
        <tab.Component key={tab.key} hidden={activeTab !== tab.key} />
      ))}
    </div>
  );
}

export default Settings;
