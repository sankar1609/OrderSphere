/**
 * "12 orders · page 2 of 3 [Previous] [Next]" under a paged list. `result` is the API's page
 * object ({items, page, size, total}); `noun` is [singular, plural].
 */
export default function Pager({
  result,
  onPage,
  noun = ["item", "items"],
  previousLabel = "Previous",
  nextLabel = "Next",
}) {
  const pages = Math.max(1, Math.ceil(result.total / result.size));
  const page = result.page;
  return (
    <p>
      {result.total} {result.total === 1 ? noun[0] : noun[1]} · page {page + 1} of {pages}{" "}
      <button type="button" onClick={() => onPage(page - 1)} disabled={page === 0}>
        {previousLabel}
      </button>{" "}
      <button type="button" onClick={() => onPage(page + 1)} disabled={page + 1 >= pages}>
        {nextLabel}
      </button>
    </p>
  );
}
