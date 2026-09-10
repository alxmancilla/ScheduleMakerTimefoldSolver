import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getTerm, updateTerm } from '../../api';
import { useToast } from '../../ui/ToastContext';

function TermTab({ hidden }) {
  const { t } = useTranslation();
  const showToast = useToast();

  const [termInput, setTermInput] = useState('');
  const [termError, setTermError] = useState(null);
  const [savingTerm, setSavingTerm] = useState(false);

  useEffect(() => {
    loadTerm();
  }, []);

  const loadTerm = async () => {
    try {
      const response = await getTerm();
      setTermInput(response.data.label || '');
      setTermError(null);
    } catch (err) {
      setTermError(t('settings.term.loadFailedPrefix') + err.message);
    }
  };

  const handleSaveTerm = async (e) => {
    e.preventDefault();
    setSavingTerm(true);
    setTermError(null);
    try {
      const response = await updateTerm(termInput.trim());
      setTermInput(response.data.label || '');
      showToast(t('settings.term.savedMessage'));
    } catch (err) {
      setTermError(err.response?.data?.message || t('settings.term.saveFailedPrefix') + err.message);
    } finally {
      setSavingTerm(false);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-term" aria-labelledby="settings-tab-term" hidden={hidden}>
      <div className="card">
        <h3>{t('settings.term.title')}</h3>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.term.description')}
        </p>
        {termError && <div className="error" role="alert">{termError}</div>}
        <form onSubmit={handleSaveTerm} style={{ display: 'flex', gap: '10px', marginTop: '10px', alignItems: 'flex-end' }}>
          <div className="form-group" style={{ marginBottom: 0, flex: 1, maxWidth: '320px' }}>
            <label htmlFor="term-label">{t('settings.term.fields.label')}</label>
            <input id="term-label"
              type="text"
              value={termInput}
              onChange={(e) => setTermInput(e.target.value)}
              maxLength={100}
              placeholder={t('settings.term.placeholder')}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={savingTerm}>
            {savingTerm ? t('settings.term.saving') : t('common.save')}
          </button>
        </form>
      </div>
    </div>
  );
}

export default TermTab;
