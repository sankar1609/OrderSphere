import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests of the Web UI against the real stack: start it first with `docker compose up -d`
 * from the repo root (global setup checks). The Vite dev server is started here if it isn't
 * running. Run with `npm run e2e`.
 */
export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.js",
  // One at a time: the tests share one backend, and several wait on its ~5s background sweeps.
  workers: 1,
  fullyParallel: false,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  outputDir: "./test-results",
  use: {
    baseURL: "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
