import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getConstraintWeights, setConstraintWeight, deleteConstraintWeight } from '../../api';
import { useToast } from '../../ui/ToastContext';

function ConstraintWeightsTab({ hidden }) {
  const { t } = useTranslation();
  const showToast = useToast();

  const [constraintWeights, setConstraintWeights] = useState([]);
  const [constraintWeightsError, setConstraintWeightsError] = useState(null);
  const [weightDrafts, setWeightDrafts] = useState({});
  const [savingWeightFor, setSavingWeightFor] = useState(null);

  useEffect(() => {
    loadConstraintWeights();
  }, []);

  // Every known constraint (from scheduler-common's SoftConstraintDefaults,
  // via GET /api/admin/constraint-config) with its default, current override
  // (if any), and effective weight. weightDrafts holds each row's editable
  // value locally until Saved.
  const loadConstraintWeights = async () => {
    try {
      const response = await getConstraintWeights();
      setConstraintWeights(response.data);
      const drafts = {};
      response.data.forEach((row) => {
        drafts[row.constraintName] = row.effectiveWeight;
      });
      setWeightDrafts(drafts);
      setConstraintWeightsError(null);
    } catch (err) {
      setConstraintWeightsError(t('settings.constraintWeights.loadFailedPrefix') + err.message);
    }
  };

  const handleWeightDraftChange = (constraintName, value) => {
    setWeightDrafts({ ...weightDrafts, [constraintName]: value });
  };

  const handleSaveWeight = async (constraintName) => {
    const value = parseInt(weightDrafts[constraintName], 10);
    if (Number.isNaN(value)) return;
    setSavingWeightFor(constraintName);
    try {
      await setConstraintWeight(constraintName, value);
      await loadConstraintWeights();
      showToast(t('settings.constraintWeights.savedMessage'));
    } catch (err) {
      setConstraintWeightsError(err.response?.data?.message || t('settings.constraintWeights.saveFailedPrefix') + err.message);
    } finally {
      setSavingWeightFor(null);
    }
  };

  const handleResetWeight = async (constraintName) => {
    setSavingWeightFor(constraintName);
    try {
      await deleteConstraintWeight(constraintName);
      await loadConstraintWeights();
      showToast(t('settings.constraintWeights.resetMessage'));
    } catch (err) {
      setConstraintWeightsError(err.response?.data?.message || t('settings.constraintWeights.saveFailedPrefix') + err.message);
    } finally {
      setSavingWeightFor(null);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-constraintWeights" aria-labelledby="settings-tab-constraintWeights" hidden={hidden}>
      <div className="card">
        <h3>{t('settings.constraintWeights.title')}</h3>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.constraintWeights.description')}
        </p>
        {constraintWeightsError && <div className="error" role="alert">{constraintWeightsError}</div>}
      </div>

      <div className="card">
        {constraintWeights.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.constraintWeights.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.constraintWeights.table.constraint')}</th>
                <th>{t('settings.constraintWeights.table.severity')}</th>
                <th>{t('settings.constraintWeights.table.defaultWeight')}</th>
                <th>{t('settings.constraintWeights.table.currentWeight')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {constraintWeights.map((row) => {
                const draft = weightDrafts[row.constraintName] ?? row.effectiveWeight;
                const isOverridden = row.overrideWeight !== null && row.overrideWeight !== undefined;
                const isDirty = String(draft) !== String(row.effectiveWeight);
                const isSaving = savingWeightFor === row.constraintName;
                const isConfigurableHard = row.defaultSeverity === 'HARD';
                const severityColor = row.effectiveSeverity === 'HARD' ? 'var(--color-danger)' : 'var(--color-success)';
                return (
                  <tr key={row.constraintName}>
                    <td>
                      {row.constraintName}
                      {isOverridden && (
                        <span style={{ marginLeft: '8px', fontSize: '11px', color: 'var(--color-warning)' }}>
                          {t('settings.constraintWeights.overridden')}
                        </span>
                      )}
                      {isConfigurableHard && !isOverridden && (
                        <div style={{ marginTop: '4px', fontSize: '11px', color: 'var(--color-text-secondary)' }}>
                          {t('settings.constraintWeights.hardToSoftHint')}
                        </div>
                      )}
                    </td>
                    <td>
                      <span
                        style={{
                          display: 'inline-block', padding: '2px 10px', borderRadius: '12px',
                          fontSize: '12px', fontWeight: 600, color: '#fff', background: severityColor,
                        }}
                      >
                        {t(`settings.constraintWeights.severities.${row.effectiveSeverity}`)}
                      </span>
                    </td>
                    <td>{row.defaultWeight}</td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        max={1000}
                        value={draft}
                        onChange={(e) => handleWeightDraftChange(row.constraintName, e.target.value)}
                        style={{ width: '80px' }}
                      />
                    </td>
                    <td>
                      <button
                        className="btn btn-primary"
                        onClick={() => handleSaveWeight(row.constraintName)}
                        disabled={!isDirty || isSaving}
                        style={{ marginRight: '5px' }}
                      >
                        {isSaving ? t('settings.constraintWeights.saving') : t('common.save')}
                      </button>
                      <button
                        className="btn btn-danger"
                        onClick={() => handleResetWeight(row.constraintName)}
                        disabled={!isOverridden || isSaving}
                      >
                        {isConfigurableHard
                          ? t('settings.constraintWeights.resetToHard')
                          : t('settings.blockRules.resetToDefault')}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default ConstraintWeightsTab;
