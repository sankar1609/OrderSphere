import { useEffect } from "react";

/** Sets the browser tab / history title for the page, e.g. "Order #42 - OrderSphere". */
export function usePageTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} - OrderSphere` : "OrderSphere";
  }, [title]);
}
