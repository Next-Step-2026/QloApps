"""FastAPI Server for Visual Inspection Quality Evaluation."""

import io
import json
import logging
import sys
import time
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, UploadFile, File, Form, Header, Request
from fastapi.responses import JSONResponse
from PIL import Image, UnidentifiedImageError
import uvicorn

from app.analyzer import evaluate_image
from app.schemas import VisualInspectionResponse, ProblemDetails, HealthzResponse

# Configure standard logger to output structured JSON
logger = logging.getLogger("visual_inspection")
logger.setLevel(logging.INFO)
handler = logging.StreamHandler(sys.stdout)
handler.setFormatter(logging.Formatter("%(message)s"))
logger.handlers = [handler]

app = FastAPI(title="Visual Inspection Quality Evaluator", version="1.0.0")


# Maximum payload limit: 5 MB (5 * 1024 * 1024 bytes)
MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024


def rfc7807_error_response(status_code: int, title: str, detail: str, instance: str = "/v1/visual-inspections") -> JSONResponse:
    """Format an RFC 7807 compliant Problem Details error response."""
    return JSONResponse(
        status_code=status_code,
        content={
            "type": "https://hotel.local/errors/invalid-image",
            "title": title,
            "status": status_code,
            "detail": detail,
            "instance": instance,
        },
        headers={"Content-Type": "application/problem+json"},
    )


@app.get("/healthz", response_model=HealthzResponse)
async def health_check():
    """Health check endpoint."""
    return {"status": "UP"}


def _validate_and_open_image(contents: bytes) -> tuple[Optional[Image.Image], Optional[JSONResponse]]:
    """Validate image payload length and decode integrity."""
    if not contents:
        return None, rfc7807_error_response(
            status_code=400,
            title="Empty File",
            detail="The uploaded file is empty.",
        )

    if len(contents) > MAX_FILE_SIZE_BYTES:
        return None, rfc7807_error_response(
            status_code=400,
            title="File Too Large",
            detail="The uploaded file exceeds the maximum allowed size of 5 MB.",
        )

    try:
        img = Image.open(io.BytesIO(contents))
        img.load()  # Force decode full image to catch corrupted data
        return img, None
    except (UnidentifiedImageError, OSError, ValueError):
        return None, rfc7807_error_response(
            status_code=400,
            title="Invalid Image File",
            detail="The uploaded file could not be decoded as a valid image.",
        )


def _log_inspection_success(
    meta: dict,
    analysis: dict,
    duration_ms: float,
) -> None:
    """Emit structured JSON log for successful image analysis."""
    log_record = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "level": "INFO",
        "correlation_id": meta.get("correlation_id"),
        "event": "IMAGE_INSPECTED",
        "room_id": meta.get("room_id"),
        "inspection_id": meta.get("inspection_id"),
        "width": analysis["metrics"]["width"],
        "height": analysis["metrics"]["height"],
        "luminance": analysis["metrics"]["luminance"],
        "sharpness": analysis["metrics"]["sharpness_score"],
        "assessment": analysis["assessment"],
        "duration_ms": duration_ms,
    }
    logger.info(json.dumps(log_record))


def _log_inspection_failure(
    meta: dict,
    error: Exception,
    duration_ms: float,
) -> None:
    """Emit structured JSON log for failed image analysis."""
    log_record = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "level": "ERROR",
        "correlation_id": meta.get("correlation_id"),
        "event": "IMAGE_INSPECTION_FAILED",
        "room_id": meta.get("room_id"),
        "inspection_id": meta.get("inspection_id"),
        "error_type": error.__class__.__name__,
        "error_message": str(error),
        "duration_ms": duration_ms,
    }
    logger.error(json.dumps(log_record), exc_info=True)


@app.post(
    "/v1/visual-inspections",
    response_model=VisualInspectionResponse,
    responses={
        400: {
            "model": ProblemDetails,
            "description": "Invalid image file or empty payload (RFC 7807)",
        },
        500: {
            "model": ProblemDetails,
            "description": "Internal server or processing error (RFC 7807)",
        },
    },
)
async def create_visual_inspection(
    file: UploadFile = File(...),
    room_id: str = Form(...),
    inspection_id: str = Form(...),
    x_correlation_id: Optional[str] = Header(default=None),
):
    """
    Evaluate room inspection photo quality.
    Validates image binary, computes luminance, sharpness, dimensions,
    and returns assessment verdict with structured logging.
    """
    start_time = time.perf_counter()
    meta = {
        "correlation_id": x_correlation_id or str(uuid.uuid4()),
        "room_id": room_id,
        "inspection_id": inspection_id,
    }

    try:
        contents = await file.read()
        img, error_response = _validate_and_open_image(contents)
        if error_response is not None:
            return error_response

        analysis = evaluate_image(img)
        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        _log_inspection_success(meta, analysis, duration_ms)

        return {
            **meta,
            "metrics": analysis["metrics"],
            "warnings": analysis["warnings"],
            "assessment": analysis["assessment"],
        }

    except Exception as e:
        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        _log_inspection_failure(meta, e, duration_ms)

        return rfc7807_error_response(
            status_code=500,
            title="Internal Processing Error",
            detail="An internal error occurred while analyzing the image. Please contact support.",
        )


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8102)
