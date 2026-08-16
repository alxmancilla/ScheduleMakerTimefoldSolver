import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * App-wide confirm dialog, replacing window.confirm(). Promise-based so
 * call sites just `if (!(await confirmAction(message))) return;` exactly
 * like the native confirm() they replace, but styled consistently with the
 * rest of the UI and accessible (focus goes to the dialog, Escape cancels).
 */
const ConfirmContext = createContext(null);

export function ConfirmProvider({ children }) {
  const { t } = useTranslation();
  const [request, setRequest] = useState(null);
  const resolver = useRef(null);

  const confirmAction = useCallback((message) => {
    return new Promise((resolve) => {
      resolver.current = resolve;
      setRequest({ message });
    });
  }, []);

  const settle = (result) => {
    setRequest(null);
    if (resolver.current) {
      resolver.current(result);
      resolver.current = null;
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') settle(false);
  };

  return (
    <ConfirmContext.Provider value={confirmAction}>
      {children}
      {request && (
        <div className="confirm-overlay" onKeyDown={handleKeyDown}>
          <div className="confirm-dialog" role="alertdialog" aria-modal="true" aria-describedby="confirm-message">
            <p id="confirm-message">{request.message}</p>
            <div className="confirm-actions">
              <button type="button" className="btn btn-secondary" onClick={() => settle(false)}>
                {t('common.cancel')}
              </button>
              <button type="button" className="btn btn-danger" autoFocus onClick={() => settle(true)}>
                {t('common.confirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}

/** Returns confirmAction(message): Promise<boolean> — resolves true if the user confirmed. */
export function useConfirm() {
  const confirmAction = useContext(ConfirmContext);
  if (!confirmAction) {
    throw new Error('useConfirm must be used within a ConfirmProvider');
  }
  return confirmAction;
}
