import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { generateBlocks, clearUnpinnedTimeslots } from '../../api';
import { useConfirm } from '../../ui/ConfirmContext';

function GenerateBlocksTab({ hidden }) {
  const { t } = useTranslation();
  const confirmAction = useConfirm();

  const [blockGenResult, setBlockGenResult] = useState(null);
  const [blockGenError, setBlockGenError] = useState(null);
  const [generatingBlocks, setGeneratingBlocks] = useState(false);

  const handleGenerateBlocks = async () => {
    setGeneratingBlocks(true);
    setBlockGenError(null);
    setBlockGenResult(null);
    try {
      const response = await generateBlocks();
      setBlockGenResult(response.data);
    } catch (err) {
      setBlockGenError(err.response?.data?.message || t('settings.generateBlocks.failedPrefix') + err.message);
    } finally {
      setGeneratingBlocks(false);
    }
  };

  const [clearTimeslotsResult, setClearTimeslotsResult] = useState(null);
  const [clearTimeslotsError, setClearTimeslotsError] = useState(null);
  const [clearingTimeslots, setClearingTimeslots] = useState(false);

  const handleClearUnpinnedTimeslots = async () => {
    if (!(await confirmAction(t('settings.generateBlocks.clearTimeslots.confirm')))) return;
    setClearingTimeslots(true);
    setClearTimeslotsError(null);
    setClearTimeslotsResult(null);
    try {
      const response = await clearUnpinnedTimeslots();
      setClearTimeslotsResult(response.data);
    } catch (err) {
      setClearTimeslotsError(
        err.response?.data?.message || t('settings.generateBlocks.clearTimeslots.failedPrefix') + err.message
      );
    } finally {
      setClearingTimeslots(false);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-generateBlocks" aria-labelledby="settings-tab-generateBlocks" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.generateBlocks.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleGenerateBlocks}
            disabled={generatingBlocks}
          >
            {generatingBlocks ? t('settings.generateBlocks.generating') : `⚙ ${t('settings.generateBlocks.button')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.generateBlocks.description')}
        </p>
        {blockGenError && <div className="error" role="alert">{blockGenError}</div>}
        {blockGenResult && (
          <div style={{ marginTop: '10px', fontSize: '13px' }}>
            <div style={{ color: '#2e7d32' }}>
              {t('settings.generateBlocks.resultSummary', {
                created: blockGenResult.blocksCreated,
                skipped: blockGenResult.groupCoursesSkippedExisting,
              })}
            </div>
            {blockGenResult.warnings.length > 0 && (
              <ul style={{ marginTop: '6px', color: 'var(--color-danger-dark)' }}>
                {blockGenResult.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.generateBlocks.clearTimeslots.title')}</h3>
          <button
            className="btn btn-danger"
            onClick={handleClearUnpinnedTimeslots}
            disabled={clearingTimeslots}
          >
            {clearingTimeslots
              ? t('settings.generateBlocks.clearTimeslots.clearing')
              : t('settings.generateBlocks.clearTimeslots.button')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.generateBlocks.clearTimeslots.description')}
        </p>
        {clearTimeslotsError && <div className="error" role="alert">{clearTimeslotsError}</div>}
        {clearTimeslotsResult && (
          <div style={{ marginTop: '10px', fontSize: '13px', color: '#2e7d32' }}>
            {t('settings.generateBlocks.clearTimeslots.resultSummary', {
              count: clearTimeslotsResult.clearedCount,
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default GenerateBlocksTab;
