"""API Contract and Schema Conformance Tests using Schemathesis."""

import os
import pytest
import schemathesis
from fastapi.testclient import TestClient
from app.main import app

# Initialize Schemathesis schema from the in-memory ASGI application's OpenAPI spec
schema = schemathesis.openapi.from_asgi("/openapi.json", app)
client = TestClient(app)
FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures")


def test_openapi_schema_structure():
    """Verify that OpenAPI specification contains all necessary schemas and endpoints."""
    spec = app.openapi()
    
    assert spec["openapi"].startswith("3.")
    assert "/healthz" in spec["paths"]
    assert "/v1/visual-inspections" in spec["paths"]
    
    components = spec.get("components", {}).get("schemas", {})
    assert "VisualInspectionResponse" in components
    assert "QualityMetrics" in components
    assert "ProblemDetails" in components
    assert "HealthzResponse" in components


def test_healthz_contract_direct():
    """Verify /healthz strictly complies with the OpenAPI contract."""
    response = client.get("/healthz")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert data["status"] == "UP"


def test_visual_inspection_200_contract_direct():
    """Verify that a successful inspection conforms to VisualInspectionResponse schema."""
    img_path = os.path.join(FIXTURES_DIR, "room_sharp.jpg")
    with open(img_path, "rb") as f:
        files = {"file": ("room_sharp.jpg", f, "image/jpeg")}
        data = {"room_id": "101", "inspection_id": "INSP-01"}
        response = client.post("/v1/visual-inspections", files=files, data=data)

    assert response.status_code == 200
    payload = response.json()
    
    # Contract schema validation
    required_fields = ["correlation_id", "inspection_id", "room_id", "metrics", "warnings", "assessment"]
    for field in required_fields:
        assert field in payload, f"Missing contract field: {field}"
        
    metrics = payload["metrics"]
    required_metrics = ["width", "height", "luminance", "sharpness_score", "luminance_status", "sharpness_status"]
    for m in required_metrics:
        assert m in metrics, f"Missing metrics contract field: {m}"


def test_visual_inspection_400_rfc7807_contract_direct():
    """Verify error responses conform to RFC 7807 ProblemDetails contract."""
    # Send empty file
    files = {"file": ("empty.jpg", b"", "image/jpeg")}
    data = {"room_id": "101", "inspection_id": "INSP-01"}
    response = client.post("/v1/visual-inspections", files=files, data=data)

    assert response.status_code == 400
    assert response.headers.get("content-type") == "application/problem+json"
    
    payload = response.json()
    rfc7807_fields = ["type", "title", "status", "detail", "instance"]
    for field in rfc7807_fields:
        assert field in payload, f"Missing RFC 7807 field: {field}"
    assert payload["status"] == 400


@schema.include(path_regex=r"^/healthz$").parametrize()
def test_schemathesis_healthz_contract(case):
    """Fuzz and validate /healthz contract via Schemathesis engine."""
    response = case.call()
    case.validate_response(response)
