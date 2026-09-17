"""Fuzz testing suite for analyzer module using Hypothesis.

Tests edge boundaries, arbitrary color spaces, extreme aspect ratios,
arbitrary pixel content, and safety invariants to ensure evaluate_image
and core analyzer functions never raise unhandled exceptions.
"""

import math
from hypothesis import given, settings, strategies as st
from PIL import Image, ImageDraw
import pytest

from app.analyzer import (
    validate_dimensions,
    calculate_luminance,
    classify_luminance,
    calculate_sharpness,
    classify_sharpness,
    evaluate_image,
    MIN_WIDTH,
    MIN_HEIGHT,
)

# Supported standard Pillow modes
SUPPORTED_IMAGE_MODES = ["RGB", "RGBA", "L", "1", "P", "CMYK", "YCbCr", "HSV"]

# Strategy for extreme and normal dimensions, including boundary cases (1, 2, 3, etc.)
dimension_fuzz_strategy = st.integers(min_value=1, max_value=2500)
extreme_dim_strategy = st.sampled_from([
    1, 2, 3, 4, 5,
    MIN_WIDTH - 1, MIN_WIDTH, MIN_WIDTH + 1,
    MIN_HEIGHT - 1, MIN_HEIGHT, MIN_HEIGHT + 1,
])


@pytest.mark.fuzz
@settings(max_examples=150, deadline=None)
@given(
    mode=st.sampled_from(SUPPORTED_IMAGE_MODES),
    width=dimension_fuzz_strategy,
    height=dimension_fuzz_strategy,
)
def test_fuzz_image_modes_and_dimensions_invariants(mode: str, width: int, height: int):
    """
    Fuzz evaluate_image across all supported PIL color modes and arbitrary dimensions.
    
    Invariants:
    1. evaluate_image must never raise any exception.
    2. assessment must be one of the canonical statuses: EVIDENCE_VALID or EVIDENCE_REQUIRES_RETAKE.
    3. metrics dictionary must contain all expected keys with finite values.
    4. luminance must remain within [0.0, 255.0].
    5. sharpness_score must be >= 0.0.
    6. width and height in metrics must accurately match image.size.
    """
    img = Image.new(mode, (width, height))
    result = evaluate_image(img)

    assert result["assessment"] in {"EVIDENCE_VALID", "EVIDENCE_REQUIRES_RETAKE"}
    assert isinstance(result["warnings"], list)

    metrics = result["metrics"]
    assert metrics["width"] == width
    assert metrics["height"] == height
    assert isinstance(metrics["luminance"], float)
    assert 0.0 <= metrics["luminance"] <= 255.0
    assert metrics["luminance_status"] in {"UNDEREXPOSED", "OPTIMAL", "OVEREXPOSED"}

    assert isinstance(metrics["sharpness_score"], float)
    assert metrics["sharpness_score"] >= 0.0
    assert metrics["sharpness_status"] in {"BLURRY", "SHARP"}


@pytest.mark.fuzz
@settings(max_examples=100, deadline=None)
@given(
    w=extreme_dim_strategy,
    h=extreme_dim_strategy,
)
def test_fuzz_extreme_small_and_boundary_geometries(w: int, h: int):
    """
    Fuzz tiny images (1x1, 2x2, etc.) where 1px crop in calculate_sharpness or
    convolution kernels could trigger boundary/zero-dimension errors.
    """
    img = Image.new("RGB", (w, h), color=(120, 130, 140))
    
    # Sharpness calculation must gracefully handle boundary sizes <= 2
    sharpness = calculate_sharpness(img)
    assert sharpness >= 0.0
    assert not math.isnan(sharpness)
    assert not math.isinf(sharpness)

    # Luminance must compute without error
    lum = calculate_luminance(img)
    assert 0.0 <= lum <= 255.0

    # Dimension validation must accurately classify
    is_valid, out_w, out_h = validate_dimensions(img)
    assert out_w == w
    assert out_h == h
    assert is_valid == ((max(w, h) >= MIN_WIDTH) and (min(w, h) >= MIN_HEIGHT))

    # Full evaluation must succeed
    result = evaluate_image(img)
    assert result["assessment"] in {"EVIDENCE_VALID", "EVIDENCE_REQUIRES_RETAKE"}


@pytest.mark.fuzz
@settings(max_examples=100, deadline=None)
@given(
    noise_data=st.binary(min_size=100, max_size=10000),
    w=st.integers(min_value=10, max_value=100),
    h=st.integers(min_value=10, max_value=100),
)
def test_fuzz_random_pixel_noise_buffers(noise_data: bytes, w: int, h: int):
    """
    Fuzz luminance and sharpness with pseudo-random byte buffers
    to test edge filters against extreme noise, high entropy, and gradient extremes.
    """
    # Create image from raw bytes with appropriate size
    total_pixels = w * h
    if len(noise_data) < total_pixels:
        noise_data = (noise_data * (total_pixels // len(noise_data) + 1))[:total_pixels]
    else:
        noise_data = noise_data[:total_pixels]

    img = Image.frombytes("L", (w, h), noise_data)
    result = evaluate_image(img)

    assert result["assessment"] in {"EVIDENCE_VALID", "EVIDENCE_REQUIRES_RETAKE"}
    assert 0.0 <= result["metrics"]["luminance"] <= 255.0
    assert result["metrics"]["sharpness_score"] >= 0.0
    assert not math.isnan(result["metrics"]["sharpness_score"])


@pytest.mark.fuzz
@settings(max_examples=80, deadline=None)
@given(
    num_lines=st.integers(min_value=1, max_value=20),
    num_rects=st.integers(min_value=1, max_value=10),
    bg_color=st.integers(min_value=0, max_value=255),
)
def test_fuzz_arbitrary_geometric_shapes(num_lines: int, num_rects: int, bg_color: int):
    """
    Fuzz sharp contrasting geometry and patterns to stress test variance calculations.
    """
    img = Image.new("RGB", (300, 300), color=(bg_color, bg_color, bg_color))
    draw = ImageDraw.Draw(img)

    for i in range(num_lines):
        x = (i * 37) % 300
        draw.line([(x, 0), (300 - x, 300)], fill=(255 - bg_color, 128, 64), width=2)

    for j in range(num_rects):
        p1 = (j * 20) % 250
        p2 = (j * 30) % 250
        draw.rectangle([p1, p2, p1 + 40, p2 + 40], outline=(0, 255, 0), fill=(50, 50, 200))

    result = evaluate_image(img)
    assert result["assessment"] in {"EVIDENCE_VALID", "EVIDENCE_REQUIRES_RETAKE"}
    assert result["metrics"]["sharpness_score"] >= 0.0


@pytest.mark.fuzz
@settings(max_examples=100, deadline=None)
@given(
    lum=st.floats(min_value=-1e9, max_value=1e9, allow_nan=False, allow_infinity=False)
)
def test_fuzz_classify_luminance_arbitrary_floats(lum: float):
    """
    Ensure classify_luminance is robust to extreme or abnormal floating point numbers.
    """
    status = classify_luminance(lum)
    assert status in {"UNDEREXPOSED", "OPTIMAL", "OVEREXPOSED"}


@pytest.mark.fuzz
@settings(max_examples=100, deadline=None)
@given(
    score=st.floats(min_value=-1e9, max_value=1e9, allow_nan=False, allow_infinity=False)
)
def test_fuzz_classify_sharpness_arbitrary_floats(score: float):
    """
    Ensure classify_sharpness is robust to extreme or negative floating point numbers.
    """
    status = classify_sharpness(score)
    assert status in {"BLURRY", "SHARP"}
