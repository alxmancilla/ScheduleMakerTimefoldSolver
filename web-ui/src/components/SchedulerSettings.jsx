import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import SolverTab from './settings/SolverTab';
import ConstraintWeightsTab from './settings/ConstraintWeightsTab';

// SCHEDULER's own page (added 2026-09-07, split out of Settings.jsx so
// SCHEDULER has a dedicated nav entry instead of browsing an "Admin" menu
// for the two things it actually has access to - see SecurityConfig's
// /api/admin/engine/**+/api/admin/constraint-config/** carve-out, which
// this page's two tabs exactly mirror). Reached via SchedulerRoute
// (SCHEDULER/ADMIN) in App.jsx. Same self-contained-tab-component shell as
// Settings.jsx, just with two tabs instead of nine.
const SCHEDULER_TABS = [
  { key: 'solver', labelKey: 'settings.solver.title', Component: SolverTab },
  { key: 'constraintWeights', labelKey: 'settings.constraintWeights.title', Component: ConstraintWeightsTab },
];

function SchedulerSettings() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('solver');

  return (
    <div>
      <div className="card">
        <h2>{t('schedulerSettings.title')}</h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>{t('schedulerSettings.description')}</p>
      </div>

      <div className="tabs" role="tablist">
        {SCHEDULER_TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            id={`scheduler-settings-tab-${tab.key}`}
            role="tab"
            aria-selected={activeTab === tab.key}
            aria-controls={`scheduler-settings-panel-${tab.key}`}
            className={`tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {SCHEDULER_TABS.map((tab) => (
        <tab.Component key={tab.key} hidden={activeTab !== tab.key} />
      ))}
    </div>
  );
}

export default SchedulerSettings;
