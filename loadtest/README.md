# Load tests: the product catalog and the order list

Two k6 tests: the catalog with its cache off and on (`run.sh`), and My Orders for customers with a
long order history (`orders.sh`, [below](#the-order-list)).

## The catalog

A [k6](https://k6.io) test of the two catalog reads (`GET /inventory/products` and
`/inventory/products/{sku}`), run with the catalog cache off and then on so the two can be compared.
k6 runs from its Docker image on the compose network - nothing to install.

```bash
docker compose up -d                          # the stack must be running
loadtest/run.sh                               # ordinary load: 50 users pausing up to 0.2s
THINK=0 VUS=100 HOLD=45s loadtest/run.sh      # saturation: 100 users, no pauses
DIRECT=1 THINK=0 VUS=100 HOLD=45s loadtest/run.sh   # same, reading inventory-service directly
```

A run takes about 5 minutes. For its duration only, `run.sh` raises the gateway's rate limit (one
load generator is one client; the default 50 requests/second would throttle it) and restarts
inventory-service with `CATALOG_CACHE_ENABLED=false`, then `true`. Both are put back on exit.

### What the test does

- **Browse:** virtual users read the whole catalog (80%) or one product (20%), like the Web UI's
  Products and Place Order pages.
- **Restock:** every 2 seconds a vendor restocks a product, so the cache is measured while it is
  being invalidated. Each restock is followed by a read that must show the new quantity - the
  "fresh right after a restock" row.
- The run fails if more than 1% of reads fail or any read after a restock is stale.

Raw summaries are written to `loadtest/results/` (git-ignored).

### Results (13 October 2026)

These runs read the **whole** catalog (224 products, 42 KB), before the catalog was paged. Since 25
October the browse scenario reads the first page (50 products, the Products page's default); see
[After pagination](#after-pagination-25-october-2026).

MacBook, Docker Desktop with 8 CPUs and 7.65 GB shared by the whole stack and the load generator;
224 products in the catalog (a 42 KB response); 100% of requests traced.

**Saturation, through the gateway** (100 users, no pauses; two runs each):

| | cache off | cache on |
|---|---|---|
| Catalog list, requests/second | 1,543 / 1,624 | 2,328 / 2,347 |
| Catalog list, median | 39.8 / 38.4 ms | 26.0 / 25.9 ms |
| Catalog list, 95th percentile | 95.1 / 88.5 ms | 62.5 / 61.6 ms |
| Errors | 0% | 0% |
| Fresh right after a restock | 100% | 100% |

**Saturation, inventory-service read directly** (one run):

| | cache off | cache on |
|---|---|---|
| Catalog list, requests/second | 2,374 | 5,671 |
| Catalog list, median | 17.3 ms | 9.5 ms |
| Catalog list, 95th percentile | 86.4 ms | 26.8 ms |

**Ordinary load, through the gateway** (50 users with pauses, about 425 requests/second; one run):

| | cache off | cache on |
|---|---|---|
| Catalog list, median | 1.7 ms | 1.2 ms |
| Catalog list, 95th percentile | 5.5 ms | 3.9 ms |
| Catalog list, 99th percentile | 13.5 ms | 9.6 ms |

How to read them:

- The service itself serves about 2.4 times as many catalog reads with the cache on. Through the
  gateway the gain is about 1.5 times, because the gateway (proxying, rate-limit check, tracing)
  becomes the limit.
- At ordinary load the database path is already fast for a catalog this size; the cache takes
  roughly a quarter off the response times.
- These are one laptop's numbers with the load generator on the same machine. Compare the two
  columns of one run; don't read the absolute figures as production capacity.

### After pagination (25 October 2026)

Ordinary load through the gateway (50 users with pauses), the catalog's first page of 50 products
(291 in the catalog); one run:

| | cache off | cache on |
|---|---|---|
| Catalog page, requests/second | 328 | 334 |
| Catalog page, median | 1.8 ms | 1.3 ms |
| Catalog page, 95th percentile | 14.6 ms | 8.4 ms |
| Catalog page, 99th percentile | 76.2 ms | 58.1 ms |
| Errors | 0% | 0% |
| Fresh right after a restock | 100% | 100% |

Requests/second is set by the users' pauses here, not by the service; the cache still takes about a
quarter off the median and nearly half off the 95th percentile, and every read right after a
restock was fresh (each page is its own cache entry, all invalidated by one version token).

### What the first runs found

- **The first cache design was dropped.** It kept the catalog JSON in Redis and fetched all 42 KB
  of it over the service's single Redis connection on every read. Its runs showed no gain, but
  they were taken before the sleeping-laptop problem below was found, so there is no trustworthy
  figure for it. The cache now keeps the JSON in the service's memory and reads only a small
  version token from Redis (see `CatalogCache`); the results above are for that design.
- **Unbounded JVMs.** The compose services had no memory limits, the Docker VM filled up and
  inventory-service was OOM-killed mid-test. Every JVM in `docker-compose.yml` now has a
  `mem_limit` and `-XX:MaxRAMPercentage=60`, and Jaeger's in-memory trace store is capped.

## The order list

`orders.js` gives each of 5 customers 500 orders (placed through the whole saga in setup, a few
minutes), then 50 virtual users read My Orders (`GET /ordersphere-orders/orders`) for 60 seconds.
It reads both response shapes, so the same script measured the list before and after it was paged.

```bash
LABEL=orders-before loadtest/orders.sh        # result in loadtest/results/<LABEL>.json
HISTORY=500 CUSTOMERS=5 VUS=50 THINK=0.2 loadtest/orders.sh   # the defaults
```

### Results (25 October 2026)

Same machine as above; through the gateway; 50 users pausing up to 0.2s; one run each.

| | before: every order | after: a page of 20 |
|---|---|---|
| Orders per response | 500 | 20 |
| Response size | 226 KB | 9 KB |
| Reads/second | 42 | 418 |
| Median | 890 ms | 2.0 ms |
| 95th percentile | 1,692 ms | 7.8 ms |
| 99th percentile | 2,083 ms | 26.5 ms |
| Errors | 0% | 0% |

How to read them:

- Before, every read loaded all 500 orders and then each order's items with a query of its own
  (501 queries), and sent 226 KB. The service, not the users' pauses, was the limit: 50 users got
  42 reads a second between them.
- After, a read is at most three queries - the page, its count, and all 20 orders' items at once
  (`hibernate.default_batch_fetch_size`) - on an index matching the query (customer, newest
  first). 418 reads a second is close to what 50 users pausing up to 0.2s can ask for, so this
  run doesn't show the service's ceiling.
- Before is measured against the code as it was (the images built before the change); both runs
  used fresh customers with the same history.

## Before trusting a run

- **The machine must stay awake.** A Mac that sleeps mid-run freezes the containers; the result is
  low throughput, inflated times and 401s (access tokens expire while it sleeps). `run.sh` wakes
  the Mac and holds it awake, but it can't stop a closed lid.
- Don't run builds, tests or anything else heavy at the same time.
- Run it twice. Two runs should agree within a few percent; if they don't, something else was
  using the machine.
