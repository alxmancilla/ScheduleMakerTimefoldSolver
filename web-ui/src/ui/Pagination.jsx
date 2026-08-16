import React, { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

export const DEFAULT_PAGE_SIZE = 15;

/**
 * Client-side pagination over an already-loaded array (the app's lists are
 * small enough that everything is fetched in one request; this just avoids
 * rendering hundreds of <tr> at once). `page` is clamped to the current
 * pageCount on every render, so shrinking the input (a search filter
 * narrowing results, a row being deleted) automatically snaps back to a
 * valid page with no extra effect/reset wiring needed.
 */
export function usePagination(items, pageSize = DEFAULT_PAGE_SIZE) {
  const [page, setPage] = useState(1);
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
  const safePage = Math.min(page, pageCount);

  const pageItems = useMemo(() => {
    const start = (safePage - 1) * pageSize;
    return items.slice(start, start + pageSize);
  }, [items, safePage, pageSize]);

  return { page: safePage, setPage, pageCount, pageItems, totalItems: items.length };
}

export function Pagination({ page, pageCount, totalItems, pageSize, onPageChange }) {
  const { t } = useTranslation();
  if (totalItems === 0) return null;

  const start = (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, totalItems);

  return (
    <div className="pagination">
      <span className="pagination-summary">
        {t('common.pagination.showing', { start, end, total: totalItems })}
      </span>
      <div className="pagination-controls">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          {t('common.pagination.previous')}
        </button>
        <span className="pagination-page">{t('common.pagination.pageOf', { page, pageCount })}</span>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={page >= pageCount}
          onClick={() => onPageChange(page + 1)}
        >
          {t('common.pagination.next')}
        </button>
      </div>
    </div>
  );
}
