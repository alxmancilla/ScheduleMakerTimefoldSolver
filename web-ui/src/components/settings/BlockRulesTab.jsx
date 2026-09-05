import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getComponentBlockRules, setComponentBlockRule, deleteComponentBlockRule, getCourseDesignations,
} from '../../api';
import { useToast } from '../../ui/ToastContext';
import { useConfirm } from '../../ui/ConfirmContext';

const BLOCK_SIZES = [1, 2, 3, 4];
// marginDays uses '' (not a number) to mean "unset - use the hardcoded default" - distinct from 0, a real override.
const EMPTY_BLOCK_RULE_FORM = { component: '', preferredBlockSize: 2, maxBlocksPerDay: 2, marginDays: '' };

function BlockRulesTab({ hidden }) {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();

  const [blockRules, setBlockRules] = useState([]);
  const [blockRulesError, setBlockRulesError] = useState(null);
  const [courseDesignations, setCourseDesignations] = useState([]);
  const [showBlockRuleForm, setShowBlockRuleForm] = useState(false);
  const [editingBlockRuleComponent, setEditingBlockRuleComponent] = useState(null);
  const [blockRuleForm, setBlockRuleForm] = useState(EMPTY_BLOCK_RULE_FORM);
  const [savingBlockRule, setSavingBlockRule] = useState(false);

  useEffect(() => {
    loadBlockRules();
    loadCourseDesignations();
  }, []);

  const loadBlockRules = async () => {
    try {
      const response = await getComponentBlockRules();
      setBlockRules(response.data);
      setBlockRulesError(null);
    } catch (err) {
      setBlockRulesError(t('settings.blockRules.loadFailedPrefix') + err.message);
    }
  };

  const loadCourseDesignations = async () => {
    try {
      const response = await getCourseDesignations();
      setCourseDesignations(response.data);
    } catch (err) {
      // Non-critical: the "add rule" component dropdown just won't have options.
    }
  };

  const unconfiguredComponents = courseDesignations.filter(
    (c) => !blockRules.some((r) => r.component === c)
  );

  const handleAddBlockRule = () => {
    setEditingBlockRuleComponent(null);
    setBlockRuleForm({ component: unconfiguredComponents[0] || '', preferredBlockSize: 2, maxBlocksPerDay: 2, marginDays: '' });
    setBlockRulesError(null);
    setShowBlockRuleForm(true);
  };

  const handleEditBlockRule = (rule) => {
    setEditingBlockRuleComponent(rule.component);
    setBlockRuleForm({
      component: rule.component,
      preferredBlockSize: rule.preferredBlockSize,
      maxBlocksPerDay: rule.maxBlocksPerDay,
      marginDays: rule.marginDays === null || rule.marginDays === undefined ? '' : rule.marginDays,
    });
    setBlockRulesError(null);
    setShowBlockRuleForm(true);
  };

  const handleCancelBlockRule = () => {
    setShowBlockRuleForm(false);
    setEditingBlockRuleComponent(null);
    setBlockRulesError(null);
  };

  const handleSubmitBlockRule = async (e) => {
    e.preventDefault();
    setSavingBlockRule(true);
    setBlockRulesError(null);
    try {
      const marginDays = blockRuleForm.marginDays === '' ? null : blockRuleForm.marginDays;
      await setComponentBlockRule(blockRuleForm.component, blockRuleForm.preferredBlockSize, blockRuleForm.maxBlocksPerDay, marginDays);
      handleCancelBlockRule();
      loadBlockRules();
      showToast(t('settings.blockRules.savedMessage'));
    } catch (err) {
      setBlockRulesError(err.response?.data?.message || t('settings.blockRules.saveFailedPrefix') + err.message);
    } finally {
      setSavingBlockRule(false);
    }
  };

  const handleDeleteBlockRule = async (component) => {
    if (!(await confirmAction(t('settings.blockRules.confirmDelete', { component })))) return;
    try {
      await deleteComponentBlockRule(component);
      loadBlockRules();
      showToast(t('settings.blockRules.deletedMessage'));
    } catch (err) {
      setBlockRulesError(err.response?.data?.message || t('settings.blockRules.deleteFailedPrefix') + err.message);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-blockRules" aria-labelledby="settings-tab-blockRules" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.blockRules.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleAddBlockRule}
            disabled={unconfiguredComponents.length === 0}
          >
            {t('settings.blockRules.addRule')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.blockRules.description')}
        </p>
        {blockRulesError && <div className="error" role="alert">{blockRulesError}</div>}
      </div>

      {showBlockRuleForm && (
        <div className="card">
          <h3>{editingBlockRuleComponent ? t('settings.blockRules.editRule') : t('settings.blockRules.newRule')}</h3>
          <form onSubmit={handleSubmitBlockRule}>
            <div className="form-group">
              <label htmlFor="block-rule-component">{t('settings.blockRules.fields.component')}</label>
              {editingBlockRuleComponent ? (
                <input id="block-rule-component" type="text" value={blockRuleForm.component} disabled />
              ) : (
                <select id="block-rule-component"
                  value={blockRuleForm.component}
                  onChange={(e) => setBlockRuleForm({ ...blockRuleForm, component: e.target.value })}
                  required
                >
                  {unconfiguredComponents.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              )}
            </div>
            <div className="form-group">
              <label htmlFor="block-rule-preferred-size">{t('settings.blockRules.fields.preferredBlockSize')}</label>
              <select id="block-rule-preferred-size"
                value={blockRuleForm.preferredBlockSize}
                onChange={(e) => setBlockRuleForm({ ...blockRuleForm, preferredBlockSize: parseInt(e.target.value, 10) })}
              >
                {BLOCK_SIZES.map((size) => (
                  <option key={size} value={size}>{size}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('settings.blockRules.sizeHint')}
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="block-rule-max-per-day">{t('settings.blockRules.fields.maxBlocksPerDay')}</label>
              <select id="block-rule-max-per-day"
                value={blockRuleForm.maxBlocksPerDay}
                onChange={(e) => setBlockRuleForm({ ...blockRuleForm, maxBlocksPerDay: parseInt(e.target.value, 10) })}
              >
                {BLOCK_SIZES.map((size) => (
                  <option key={size} value={size}>{size}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('settings.blockRules.maxBlocksPerDayHint')}
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="block-rule-margin-days">{t('settings.blockRules.fields.marginDays')}</label>
              <select id="block-rule-margin-days"
                value={blockRuleForm.marginDays}
                onChange={(e) => setBlockRuleForm({
                  ...blockRuleForm,
                  marginDays: e.target.value === '' ? '' : parseInt(e.target.value, 10),
                })}
              >
                <option value="">{t('settings.blockRules.marginDaysDefault')}</option>
                {[0, 1, 2, 3, 4].map((days) => (
                  <option key={days} value={days}>{days}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('settings.blockRules.marginDaysHint')}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingBlockRule || !blockRuleForm.component}>
                {savingBlockRule ? t('settings.blockRules.saving') : t('common.save')}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleCancelBlockRule}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {blockRules.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.blockRules.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.blockRules.table.component')}</th>
                <th>{t('settings.blockRules.table.preferredBlockSize')}</th>
                <th>{t('settings.blockRules.table.maxBlocksPerDay')}</th>
                <th>{t('settings.blockRules.table.marginDays')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {blockRules.map((rule) => (
                <tr key={rule.component}>
                  <td>{rule.component}</td>
                  <td>{rule.preferredBlockSize}h</td>
                  <td>{rule.maxBlocksPerDay}</td>
                  <td>{rule.marginDays === null || rule.marginDays === undefined
                    ? t('settings.blockRules.marginDaysDefault')
                    : rule.marginDays}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEditBlockRule(rule)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDeleteBlockRule(rule.component)}>
                      {t('settings.blockRules.resetToDefault')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default BlockRulesTab;
