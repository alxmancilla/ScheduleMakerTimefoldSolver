import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { runEngine, getEngineStatus } from '../../api';
import { ENGINE_POLL_MS, ENGINE_RUN_FINISHED_EVENT, formatTimestamp } from './constants';

function SolverTab({ hidden }) {
  const { t } = useTranslation();

  const [engineStatus, setEngineStatus] = useState(null);
  const [engineError, setEngineError] = useState(null);
  // Pre-filled with solverConfig.xml's own defaults; omitted from the request
  // (left as null) only if the field is cleared entirely, which leaves that
  // value as the XML defines it.
  const [minutesSpentLimit, setMinutesSpentLimit] = useState(5);
  const [unimprovedMinutesSpentLimit, setUnimprovedMinutesSpentLimit] = useState(2);
  const [skipValidation, setSkipValidation] = useState(false);
  // "Option B": solverConfig.xml's own seed is fixed, so a warm-started
  // re-solve of unchanged data tends to land back on the same locally-optimal
  // arrangement. randomizeSeed requests a fresh, freshly-logged seed instead;
  // fixedSeed replays one exact seed (e.g. copied from a past run's log or
  // schedule_run.random_seed) - mutually exclusive, both default off/empty
  // (solverConfig.xml's own seed used unchanged).
  const [randomizeSeed, setRandomizeSeed] = useState(false);
  const [fixedSeed, setFixedSeed] = useState('');
  const pollRef = useRef(null);

  useEffect(() => {
    loadEngineStatus();
    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(loadEngineStatus, ENGINE_POLL_MS);
  };

  const loadEngineStatus = async () => {
    try {
      const response = await getEngineStatus();
      setEngineStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!pollRef.current) startPolling();
      } else {
        stopPolling();
        // A run that just finished may have added a new compliance snapshot -
        // see ENGINE_RUN_FINISHED_EVENT's own doc comment for why this is a
        // window event rather than a prop.
        window.dispatchEvent(new Event(ENGINE_RUN_FINISHED_EVENT));
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const handleRunEngine = async () => {
    setEngineError(null);
    try {
      const randomSeed = randomizeSeed ? 'random' : (fixedSeed.trim() === '' ? null : fixedSeed.trim());
      const response = await runEngine({
        minutesSpentLimit: minutesSpentLimit === '' ? null : minutesSpentLimit,
        unimprovedMinutesSpentLimit: unimprovedMinutesSpentLimit === '' ? null : unimprovedMinutesSpentLimit,
        skipValidation,
        randomSeed,
      });
      setEngineStatus(response.data);
      startPolling();
    } catch (err) {
      setEngineError(err.response?.data?.message || t('settings.solver.startFailedPrefix') + err.message);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-solver" aria-labelledby="settings-tab-solver" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <h3>{t('settings.solver.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleRunEngine}
            disabled={engineStatus?.state === 'RUNNING'}
          >
            {engineStatus?.state === 'RUNNING' ? t('settings.solver.running') : `▶ ${t('settings.solver.startEngine')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.solver.description')}
        </p>
        <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap', marginTop: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="minutesSpentLimit">{t('settings.solver.minutesSpentLimit')}</label>
            <input
              id="minutesSpentLimit"
              type="number"
              min="1"
              max="120"
              value={minutesSpentLimit}
              onChange={(e) => setMinutesSpentLimit(e.target.value === '' ? '' : Number(e.target.value))}
              disabled={engineStatus?.state === 'RUNNING'}
              style={{ width: '70px', padding: '6px' }}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="unimprovedMinutesSpentLimit">{t('settings.solver.unimprovedMinutesSpentLimit')}</label>
            <input
              id="unimprovedMinutesSpentLimit"
              type="number"
              min="1"
              max="60"
              value={unimprovedMinutesSpentLimit}
              onChange={(e) => setUnimprovedMinutesSpentLimit(e.target.value === '' ? '' : Number(e.target.value))}
              disabled={engineStatus?.state === 'RUNNING'}
              style={{ width: '70px', padding: '6px' }}
            />
          </div>
        </div>
        <p style={{ marginTop: '6px', color: 'var(--color-text-secondary)', fontSize: '12px' }}>
          {t('settings.solver.limitsDescription')}
        </p>
        <div style={{ marginTop: '12px', display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
          <input
            id="skipValidation"
            type="checkbox"
            checked={skipValidation}
            onChange={(e) => setSkipValidation(e.target.checked)}
            disabled={engineStatus?.state === 'RUNNING'}
            style={{ marginTop: '3px' }}
          />
          <label htmlFor="skipValidation" style={{ fontSize: '13px' }}>
            {t('settings.solver.skipValidation')}
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '2px' }}>
              {t('settings.solver.skipValidationHint')}
            </div>
          </label>
        </div>
        <div style={{ marginTop: '12px', display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
          <input
            id="randomizeSeed"
            type="checkbox"
            checked={randomizeSeed}
            onChange={(e) => {
              setRandomizeSeed(e.target.checked);
              if (e.target.checked) setFixedSeed('');
            }}
            disabled={engineStatus?.state === 'RUNNING'}
            style={{ marginTop: '3px' }}
          />
          <label htmlFor="randomizeSeed" style={{ fontSize: '13px' }}>
            {t('settings.solver.randomizeSeed')}
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '2px' }}>
              {t('settings.solver.randomizeSeedHint')}
            </div>
          </label>
        </div>
        <div style={{ marginTop: '10px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <label htmlFor="fixedSeed">{t('settings.solver.fixedSeed')}</label>
          <input
            id="fixedSeed"
            type="text"
            inputMode="numeric"
            placeholder={t('settings.solver.fixedSeedPlaceholder')}
            value={fixedSeed}
            onChange={(e) => {
              setFixedSeed(e.target.value);
              if (e.target.value.trim() !== '') setRandomizeSeed(false);
            }}
            disabled={engineStatus?.state === 'RUNNING' || randomizeSeed}
            style={{ width: '180px', padding: '6px' }}
          />
        </div>
        {engineError && <div className="error" role="alert">{engineError}</div>}
        {engineStatus && (
          <div style={{ marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '20px', fontSize: '13px', flexWrap: 'wrap' }}>
              <span><strong>{t('settings.solver.state')}</strong> {engineStatus.state}</span>
              <span><strong>{t('settings.solver.started')}</strong> {formatTimestamp(engineStatus.startedAt)}</span>
              <span><strong>{t('settings.solver.finished')}</strong> {formatTimestamp(engineStatus.finishedAt)}</span>
              <span><strong>{t('settings.solver.exitCode')}</strong> {engineStatus.exitCode ?? '-'}</span>
            </div>
            {engineStatus.log && engineStatus.log.length > 0 && (
              <pre
                style={{
                  marginTop: '10px',
                  maxHeight: '260px',
                  overflowY: 'auto',
                  background: '#1e1e1e',
                  color: '#d4d4d4',
                  padding: '10px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {engineStatus.log.join('\n')}
              </pre>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default SolverTab;
