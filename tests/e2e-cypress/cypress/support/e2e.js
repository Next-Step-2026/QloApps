// cypress/support/e2e.js
import "./commands";

// Prevent Cypress from failing tests when QloApps scripts throw uncaught exceptions
Cypress.on("uncaught:exception", (err, runnable) => {
  return false;
});
