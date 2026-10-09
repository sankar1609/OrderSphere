import { GATEWAY } from "./helpers.js";

/** Fails fast, with what to do about it, when the backend stack isn't up. */
export default async function globalSetup() {
  const services = ["auth-service", "ordersphere-orders", "inventory-service", "payment-service"];
  for (const service of services) {
    let status;
    try {
      status = (await fetch(`${GATEWAY}/${service}/actuator/health/readiness`)).status;
    } catch {
      status = "no answer";
    }
    if (status !== 200) {
      throw new Error(
        `${service} isn't ready behind the gateway at ${GATEWAY} (${status}). ` +
          "Start the stack from the repo root with `docker compose up -d` and wait until it's healthy."
      );
    }
  }
}
