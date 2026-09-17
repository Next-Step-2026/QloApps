describe("Visual Inspection & Housekeeping Quality Evaluator (QLO-FEAT-002)", () => {
  beforeEach(() => {
    cy.loginAdmin();
    cy.goToVisualInspection();
  });

  it("Scenario 1: should approve inspection when all 3 photos are sharp and well-lit (EVIDENCE_VALID)", () => {
    // Select first room available
    cy.get('select[name="room_id"]').then(($select) => {
      const firstOptionValue = $select.find("option").first().val();
      cy.get('select[name="room_id"]').select(firstOptionValue);
    });

    // Upload sharp fixtures for all 3 checklist items
    cy.get('input[name="photo_bed"]').selectFile("cypress/fixtures/room_sharp.jpg");
    cy.get('input[name="photo_bath"]').selectFile("cypress/fixtures/room_sharp.jpg");
    cy.get('input[name="photo_amenities"]').selectFile("cypress/fixtures/room_sharp.jpg");

    // Submit form
    cy.get('button[name="submitInspection"]').click();

    // Verify overall master banner
    cy.get(".alert-success").should("be.visible");
    cy.get(".alert-success").should("contain.text", "ALL EVIDENCES VALID");

    // Verify all 3 item cards report VALID badges
    cy.get(".badge-success").should("have.length.at.least", 3);
    cy.contains("VALID").should("be.visible");

    // Verify audit trail table contains at least one approved record
    cy.get("table").should("exist");
    cy.contains("ALL EVIDENCES VALID").should("be.visible");
  });

  it("Scenario 2: should display RETAKE warning when photos are dark or blurry (EVIDENCE_REQUIRES_RETAKE)", () => {
    cy.get('select[name="room_id"]').then(($select) => {
      const firstOptionValue = $select.find("option").first().val();
      cy.get('select[name="room_id"]').select(firstOptionValue);
    });

    // Upload dark photo for Bed and blurry photo for Bath, sharp for Amenities
    cy.get('input[name="photo_bed"]').selectFile("cypress/fixtures/room_dark.jpg");
    cy.get('input[name="photo_bath"]').selectFile("cypress/fixtures/room_blurry.jpg");
    cy.get('input[name="photo_amenities"]').selectFile("cypress/fixtures/room_sharp.jpg");

    cy.get('button[name="submitInspection"]').click();

    // Verify master banner displays retake requirement
    cy.get(".alert-danger").should("be.visible");
    cy.get(".alert-danger").should("contain.text", "RETAKE NON-COMPLIANT PHOTOS");

    // Verify individual retake badges and specific metric warnings
    cy.get(".badge-danger").should("have.length.at.least", 2);
    cy.contains("Photo is too dark", { matchCase: false }).should("be.visible");
  });

  it("Scenario 3: should enforce required file inputs on client-side", () => {
    // Attempt to submit without attaching files
    cy.get('button[name="submitInspection"]').click();

    // HTML5 native validation prevents submit when fields are empty
    cy.get('input[name="photo_bed"]').then(($input) => {
      expect($input[0].checkValidity()).to.be.false;
    });
  });
});
