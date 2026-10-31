import { useSearchParams } from "react-router";

/**
 * The page of a list (and its search, if it has one) kept in the address - `?page=2&q=teapot` -
 * so refresh, back/forward and links keep the reader's place. `page` in the address counts from 1
 * like the "page X of Y" text; the returned `page` counts from 0 like the API. A list's other
 * filters (`?status=PENDING`) live here too, through `filter` and `setFilter`.
 */
export function usePageParams() {
  const [params, setParams] = useSearchParams();
  const fromAddress = Number.parseInt(params.get("page") ?? "1", 10);
  const page = Number.isInteger(fromAddress) && fromAddress >= 1 ? fromAddress - 1 : 0;
  const query = params.get("q") ?? "";

  function update(changes, options) {
    setParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        if (value === null || value === "") {
          next.delete(key);
        } else {
          next.set(key, String(value));
        }
      }
      return next;
    }, options);
  }

  return {
    page,
    query,
    setPage: (next) => update({ page: next > 0 ? next + 1 : null }),
    // For a page that no longer exists: Back must not return to it.
    replacePage: (next) => update({ page: next > 0 ? next + 1 : null }, { replace: true }),
    // A new search starts again at its first page.
    setQuery: (next) => update({ q: next.trim(), page: null }),
    filter: (name) => params.get(name),
    // So does a new filter. null removes it (the list's default).
    setFilter: (name, value) => update({ [name]: value, page: null }),
  };
}
