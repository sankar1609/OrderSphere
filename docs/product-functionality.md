# OrderSphere — Completed Product Functionality

This document describes what OrderSphere can do today, from a business and user perspective. It intentionally avoids technical implementation details — see the accompanying technical documentation for how these capabilities are built.

## Account & Access

- Anyone can register for an account and log in securely.
- Every account has a role — **Customer**, **Vendor**, **Admin**, or **Auditor** — which determines what that person is allowed to do.
- Signed-in users can view their own account details.
- Admins can change any user's role (for example, promoting a customer to a vendor).
- A default administrator account is available from day one, so a new deployment is usable immediately without extra setup.

## Product Catalog & Inventory

- Admins and vendors can add new products to the catalog, each with a unique SKU, name, and starting stock level.
- Any signed-in user can browse the catalog or look up a specific product to check its current availability.
- Admins and vendors can restock a product, increasing the quantity on hand.
- When a customer places an order, the required stock is automatically set aside so the same inventory can't be sold to two customers at once.
- That set-aside stock is automatically confirmed once the order's payment succeeds, or automatically released back to available inventory if the order doesn't go through.
- Stock that's set aside but never confirmed or released is automatically freed up again after a period of time, so abandoned orders don't permanently tie up inventory.

## Payments

- Customers can save one or more payment methods to their account (currently card-based payments).
- Customers can remove a saved payment method.
- When an order is placed, payment is processed in the background — the customer doesn't have to wait for it to complete before their order is created.
- If a payment is declined, the system automatically undoes the order: the order is cancelled and any reserved inventory is put back.
- A completed payment can be refunded, crediting the customer.

## Ordering

- Customers can place an order for one or more products, specifying a delivery address and which saved payment method to use.
- Placing an order automatically reserves the needed inventory and starts payment processing — no separate steps required.
- Customers can view their full order history and check the current status of any order at any time.
- Customers can cancel their own order; cancelling is safe to do more than once without causing problems.
- Every order automatically moves through its lifecycle on its own — from awaiting payment, to confirmed, to shipped, to delivered — without needing manual updates.
- If anything goes wrong along the way (for example, a declined payment), the order is automatically rolled back rather than being left in a broken or inconsistent state: inventory is released and the order is marked cancelled.

## Shipping & Delivery

- Once an order is confirmed, a shipment is automatically created for it.
- Customers can check a shipment's current status and see the full history of every stage it has passed through.
- Shipments automatically progress through real-world delivery stages — created, picked, in transit, and delivered — without manual updates.
- Once a shipment has been delivered, the customer can request a return.
- Shipments can be looked up directly, or by the order they belong to.

## Notifications

- Customers are automatically notified at key moments in their order's journey — for example, when an order is confirmed or cancelled, when a payment succeeds or fails, when a shipment is created, and when a delivery is confirmed.
- Customers can choose which channels they want to be notified on (email, SMS, in-app, or push), and turn individual channels on or off.
- Customers can view their full notification history at any time.
- Notifications are delivered automatically in the background, with automatic retries if a delivery attempt doesn't succeed the first time.

## Reliability & Safety Nets

- If something fails partway through placing or fulfilling an order, the system automatically corrects course — releasing inventory, cancelling the order, and notifying the customer — without needing manual cleanup.
- Key actions (reserving stock, cancelling an order, processing a refund, etc.) are safe to repeat, so an accidental duplicate request never causes double-processing or leaves data in an inconsistent state.
