# OrderSphere — Completed Product Functionality

This document describes what OrderSphere can do today, from a business and user perspective. It intentionally avoids technical implementation details — see the accompanying technical documentation for how these capabilities are built.

## Account & Access

- Anyone can register for an account and log in securely.
- Every account has a role — **Customer**, **Vendor**, **Admin**, or **Auditor** — which determines what that person is allowed to do.
- Signed-in users can view their own account details.
- Admins can change any user's role (for example, promoting a customer to a vendor).
- A default administrator account is available from day one, so a new deployment is usable immediately without extra setup.

## Product Catalog & Inventory

- Admins and vendors can add new products to the catalog, each with a unique SKU, name, unit price, and starting stock level.
- Any signed-in user can browse the catalog or look up a specific product to check its current price and availability.
- Admins and vendors can restock a product, increasing the quantity on hand.
- When a customer places an order, the required stock is automatically set aside so the same inventory can't be sold to two customers at once.
- An order can only be placed for stock that's actually available: if any item is short (or out of stock), the whole order is declined before payment and the customer is told which item and how many are left. The store shows out-of-stock products as unavailable and won't let a customer add more than is in stock.
- That set-aside stock is automatically confirmed once the order's payment succeeds, or automatically released back to available inventory if the order doesn't go through.
- Stock that's set aside but never confirmed or released is automatically freed up again after a period of time, so abandoned orders don't permanently tie up inventory.

## Payments

- After placing an order, the customer is taken to a secure payment page run by the payment provider, where they enter their card details. OrderSphere itself never sees or stores card details.
- If a card is declined, the payment page says so and the customer can try another card; the order waits for them.
- Once the payment goes through, the customer is brought back to OrderSphere and the order is confirmed automatically.
- If the customer cancels on the payment page, or doesn't pay within 10 minutes, the order is cancelled automatically and the reserved stock is put back.
- An unpaid order can be paid later from the order list ("Pay now"), as long as the payment window hasn't expired.
- If a customer cancels an order but then pays on a payment page they still had open, the payment is refunded automatically.
- A completed payment can be refunded, crediting the customer.
- For development and testing, payments go through a dummy payment provider with test cards (4242 4242 4242 4242 succeeds, 4000 0000 0000 0002 is declined); no real money moves.

## Ordering

- Customers can place an order for one or more products, specifying a delivery address, and then pay for it on the payment page.
- Placing an order automatically reserves the needed inventory and opens the payment — no separate steps required.
- The order total is always calculated by the system from each product's catalog price; customers never enter or influence the amount charged. Each order records the unit price it was placed at, so later catalog price changes don't alter past orders.
- Customers can view their full order history and check the current status of any order at any time.
- Customers can cancel their own order; cancelling is safe to do more than once without causing problems.
- Every order automatically moves through its lifecycle on its own — from awaiting payment, to confirmed, to shipped, to delivered — without needing manual updates.
- If anything goes wrong along the way (for example, the customer abandons payment), the order is automatically rolled back rather than being left in a broken or inconsistent state: inventory is released and the order is marked cancelled.

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
