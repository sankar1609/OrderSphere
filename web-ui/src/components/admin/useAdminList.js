import { useEffect, useState } from "react";

export const ADMIN_PAGE_SIZE = 50;

/**
 * One page of an admin list, from `load(page)` -> {items, page, size, total}, reloaded when the page
 * or `deps` change and on `reload()`. An admin's action can take the last row off the last page
 * (it was retried or resolved): the list then moves back to what is now the last page.
 */
export function useAdminList(load, deps, { page, replacePage }, onUnauthorized) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let current = true;
    setResult(null);
    load(page)
      .then((data) => {
        if (!current) {
          return;
        }
        if (data.items.length === 0 && data.page > 0) {
          const lastPage = Math.ceil(data.total / data.size) - 1;
          replacePage(Math.max(0, Math.min(lastPage, data.page - 1)));
          return;
        }
        setResult(data);
        setError(null);
      })
      .catch((err) => {
        if (current) {
          err.status === 401 ? onUnauthorized() : setError(err.message);
        }
      });
    return () => {
      current = false;
    };
  }, [...deps, page, reloadKey]);

  return { result, setResult, error, reloadKey, reload: () => setReloadKey((k) => k + 1) };
}
