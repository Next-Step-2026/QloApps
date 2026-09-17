// cypress/support/commands.js

/**
 * Log in to the QloApps Back-Office
 */
Cypress.Commands.add("loginAdmin", (
  email = Cypress.env("ADMIN_EMAIL") || "rafaelsant@google.com",
  password = Cypress.env("ADMIN_PASSWORD") || "Tsuy123@"
) => {
  const adminDir = Cypress.env("ADMIN_BASE_URL") || "admin646rrpdpy";
  cy.visit(`/${adminDir}/index.php`);

  cy.get("body", { timeout: 15000 }).then(($body) => {
    // Check if we are already on the login page or if already logged in
    if ($body.find("#login_form").length > 0) {
      cy.get("#login_form #email").should("be.visible").clear().type(email);
      cy.get("#login_form #passwd").should("be.visible").clear().type(password);
      // Specifically target submit button inside #login_form
      cy.get('#login_form button[name="submitLogin"]').click();
    }
  });

  // Ensure successful login redirect away from AdminLogin
  cy.url({ timeout: 20000 }).should("not.include", "controller=AdminLogin");
});

/**
 * Navigate to the Visual Inspection controller
 */
Cypress.Commands.add("goToVisualInspection", () => {
  const adminDir = Cypress.env("ADMIN_BASE_URL") || "admin646rrpdpy";

  // Extract the href from the rendered admin menu
  cy.get('a[href*="controller=AdminVisualInspection"]', { timeout: 15000 })
    .first()
    .should("have.attr", "href")
    .then((href) => {
      // If href starts with index.php or /index.php, prefix with admin folder
      let targetUrl = href;
      if (targetUrl.startsWith("index.php")) {
        targetUrl = `/${adminDir}/${targetUrl}`;
      } else if (targetUrl.startsWith("/index.php")) {
        targetUrl = `/${adminDir}${targetUrl}`;
      } else if (!targetUrl.includes(adminDir) && targetUrl.includes("controller=AdminVisualInspection")) {
        targetUrl = `/${adminDir}/${targetUrl.replace(/^\//, "")}`;
      }
      cy.visit(targetUrl);
    });

  cy.contains("Visual Inspection & Housekeeping Photo Evidences", { timeout: 15000 }).should("be.visible");
});
