import { useEffect, useState } from "react";

/** A search box for a paged list. Searching for nothing shows everything. */
export default function SearchBox({ query, onSearch, label, placeholder, maxLength = 255 }) {
  const [text, setText] = useState(query);
  useEffect(() => setText(query), [query]);

  return (
    <form
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(text);
      }}
      style={{ marginBottom: 12 }}
    >
      <label>
        {label}{" "}
        <input
          type="search"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder={placeholder}
          maxLength={maxLength}
        />
      </label>{" "}
      <button type="submit">Search</button>
    </form>
  );
}
