const { defineConfig } = require("cypress");
const fs = require("fs");
const path = require("path");

// Read .env if present
const envPath = path.resolve(__dirname, ".env");
const dotEnv = {};
if (fs.existsSync(envPath)) {
  const content = fs.readFileSync(envPath, "utf-8");
  content.split("\n").forEach((line) => {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith("#") && trimmed.includes("=")) {
      const idx = trimmed.indexOf("=");
      dotEnv[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
    }
  });
}

const baseUrl = process.env.CYPRESS_BASE_URL || dotEnv.CYPRESS_BASE_URL || "http://localhost:8080";
const adminDir = process.env.CYPRESS_ADMIN_BASE_URL || dotEnv.CYPRESS_ADMIN_BASE_URL || "admin646rrpdpy";
const adminEmail = process.env.CYPRESS_ADMIN_EMAIL || dotEnv.CYPRESS_ADMIN_EMAIL || "rafaelsant@google.com";
const adminPassword = process.env.CYPRESS_ADMIN_PASSWORD || dotEnv.CYPRESS_ADMIN_PASSWORD || "Tsuy123@";

module.exports = defineConfig({
  env: {
    ADMIN_EMAIL: adminEmail,
    ADMIN_PASSWORD: adminPassword,
    ADMIN_BASE_URL: adminDir,
  },
  e2e: {
    baseUrl: baseUrl,
    viewportWidth: 1280,
    viewportHeight: 800,
    defaultCommandTimeout: 10000,
    requestTimeout: 10000,
    video: false,
    screenshotOnRunFailure: true,
    supportFile: "cypress/support/e2e.js",
    specPattern: "cypress/e2e/**/*.cy.{js,jsx,ts,tsx}",
    fixturesFolder: "cypress/fixtures",
    setupNodeEvents(on, config) {
      on("before:browser:launch", (browser = {}, launchOptions) => {
        if (browser.family === "chromium" || browser.name === "chrome") {
          launchOptions.args.push("--disable-gpu");
          launchOptions.args.push("--no-sandbox");
          launchOptions.args.push("--disable-dev-shm-usage");
          launchOptions.args.push("--disable-software-rasterizer");
        }
        return launchOptions;
      });
      return config;
    },
  },
});
