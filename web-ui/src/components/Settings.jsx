import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
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
// than one shared 1800-line component switching on activeTab. All 11 are
// always mounted and shown/hidden via the `hidden` attribute, never
// conditionally rendered, so every tab's data loads once up front exactly
// like the original single-component version did - switching tabs never
// re-fetches. The only cross-tab coupling (a finished solver run refreshing
// the compliance-snapshots list) goes through a window event instead of
// shared state - see settings/constants.js's ENGINE_RUN_FINISHED_EVENT.
const SETTINGS_TABS = [
  { key: 'term', labelKey: 'settings.term.title', Component: TermTab },
  { key: 'solver', labelKey: 'settings.solver.title', Component: SolverTab },
  { key: 'complianceSnapshots', labelKey: 'settings.complianceSnapshots.title', Component: ComplianceSnapshotsTab },
  { key: 'generateBlocks', labelKey: 'settings.generateBlocks.title', Component: GenerateBlocksTab },
  { key: 'blockRules', labelKey: 'settings.blockRules.title', Component: BlockRulesTab },
  { key: 'constraintWeights', labelKey: 'settings.constraintWeights.title', Component: ConstraintWeightsTab },
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
