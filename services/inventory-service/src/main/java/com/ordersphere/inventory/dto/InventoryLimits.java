package com.ordersphere.inventory.dto;

/**
 * Upper bounds on quantities and text the API accepts. They keep every stock calculation far from
 * {@code int} overflow (a quantity that wraps negative would create stock out of nothing) and every
 * value within its database column.
 */
public final class InventoryLimits {

  /** Most units of one SKU a single order line may ask for. */
  public static final int MAX_LINE_QUANTITY = 10_000;

  /** Most lines in one reservation. */
  public static final int MAX_LINES = 50;

  /** Most units a single create or restock may add. */
  public static final int MAX_STOCK_CHANGE = 1_000_000;

  /** Most units a product may hold on hand. */
  public static final int MAX_STOCK = 100_000_000;

  /** SKUs travel in URL paths, so: letters, digits, '.', '_' and '-' only (column is 64). */
  public static final String SKU_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";

  public static final int MAX_NAME_LENGTH = 255;

  private InventoryLimits() {}
}
