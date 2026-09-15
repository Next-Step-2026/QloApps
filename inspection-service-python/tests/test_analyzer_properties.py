"""Property-based tests for analyzer module using Hypothesis."""

import math
from hypothesis import given, strategies as st
from PIL import Image

from app.analyzer import (
    validate_dimensions,
    calculate_luminance,
    classify_luminance,
    calculate_sharpness,
    classify_sharpness,
    evaluate_image,
    MIN_WIDTH,
    MIN_HEIGHT,
    LUMINANCE_UNDEREXPOSED_THRESHOLD,
    LUMINANCE_OVEREXPOSED_THRESHOLD,
    SHARPNESS_MIN_THRESHOLD,
)

# Custom Strategies
dimension_strategy = st.integers(min_value=1, max_value=3000)
color_component_strategy = st.integers(min_value=0, max_value=255)
rgb_color_strategy = st.tuples(color_component_strategy, color_component_strategy, color_component_strategy)
luminance_float_strategy = st.floats(min_value=-1000.0, max_value=1000.0, allow_nan=False, allow_infinity=False)
sharpness_float_strategy = st.floats(min_value=0.0, max_value=50000.0, allow_nan=False, allow_infinity=False)
sharpness_img_size_strategy = st.tuples(st.integers(min_value=4, max_value=60), st.integers(min_value=4, max_value=60))
eval_img_size_strategy = st.tuples(st.integers(min_value=10, max_value=100), st.integers(min_value=10, max_value=100))
luminance_img_size_strategy = st.tuples(st.integers(min_value=2, max_value=80), st.integers(min_value=2, max_value=80))


# 1. validate_dimensions properties
@given(w=dimension_strategy, h=dimension_strategy)
def test_prop_validate_dimensions_orientation_symmetry(w: int, h: int):
    """
    Validation result must be symmetric under orientation transpose (W, H) vs (H, W).
    """
    img1 = Image.new("RGB", (w, h))
    img2 = Image.new("RGB", (h, w))

    valid1, out_w1, out_h1 = validate_dimensions(img1)
    valid2, out_w2, out_h2 = validate_dimensions(img2)

    assert valid1 == valid2
    assert (out_w1, out_h1) == (w, h)
    assert (out_w2, out_h2) == (h, w)


@given(w=dimension_strategy, h=dimension_strategy)
def test_prop_validate_dimensions_thresholds(w: int, h: int):
    """
    validate_dimensions should be True iff max(w, h) >= MIN_WIDTH and min(w, h) >= MIN_HEIGHT.
    """
    img = Image.new("RGB", (w, h))
    is_valid, out_w, out_h = validate_dimensions(img)

    expected = (max(w, h) >= MIN_WIDTH) and (min(w, h) >= MIN_HEIGHT)
    assert is_valid == expected
    assert out_w == w
    assert out_h == h


# 2. classify_luminance properties
@given(lum=luminance_float_strategy)
def test_prop_classify_luminance_partition(lum: float):
    """
    classify_luminance partitions the real numbers exhaustively into three disjoint categories.
    """
    classification = classify_luminance(lum)
    assert classification in {"UNDEREXPOSED", "OPTIMAL", "OVEREXPOSED"}
    assert (classification == "UNDEREXPOSED") == (lum < LUMINANCE_UNDEREXPOSED_THRESHOLD)
    assert (classification == "OVEREXPOSED") == (lum > LUMINANCE_OVEREXPOSED_THRESHOLD)
    assert (classification == "OPTIMAL") == (
        LUMINANCE_UNDEREXPOSED_THRESHOLD <= lum <= LUMINANCE_OVEREXPOSED_THRESHOLD
    )


@given(lum1=luminance_float_strategy, lum2=luminance_float_strategy)
def test_prop_classify_luminance_monotonicity(lum1: float, lum2: float):
    """
    classify_luminance must be monotonically non-decreasing.
    UNDEREXPOSED (0) <= OPTIMAL (1) <= OVEREXPOSED (2).
    """
    order = {"UNDEREXPOSED": 0, "OPTIMAL": 1, "OVEREXPOSED": 2}
    low, high = min(lum1, lum2), max(lum1, lum2)
    assert order[classify_luminance(low)] <= order[classify_luminance(high)]


# 3. calculate_luminance properties
@given(
    w=st.integers(min_value=2, max_value=80),
    h=st.integers(min_value=2, max_value=80),
    gray_val=color_component_strategy,
)
def test_prop_calculate_luminance_grayscale_uniform(w: int, h: int, gray_val: int):
    """
    A uniform grayscale image must have luminance exactly equal to gray_val.
    """
    img = Image.new("L", (w, h), color=gray_val)
    lum = calculate_luminance(img)
    assert math.isclose(lum, float(gray_val), abs_tol=1e-3)


@given(
    size=luminance_img_size_strategy,
    color=rgb_color_strategy,
)
def test_prop_calculate_luminance_bounds_and_range(size: tuple[int, int], color: tuple[int, int, int]):
    """
    Luminance of any valid RGB image must be within [0.0, 255.0].
    """
    img = Image.new("RGB", size, color=color)
    lum = calculate_luminance(img)
    assert 0.0 <= lum <= 255.0


# 4. classify_sharpness properties
@given(score=sharpness_float_strategy)
def test_prop_classify_sharpness_partition(score: float):
    """
    classify_sharpness partitions the non-negative real numbers into BLURRY or SHARP.
    """
    classification = classify_sharpness(score)
    assert classification in {"BLURRY", "SHARP"}
    assert (classification == "BLURRY") == (score < SHARPNESS_MIN_THRESHOLD)
    assert (classification == "SHARP") == (score >= SHARPNESS_MIN_THRESHOLD)


# 5. calculate_sharpness properties
@given(
    size=sharpness_img_size_strategy,
    color=rgb_color_strategy,
)
def test_prop_calculate_sharpness_uniform_image(size: tuple[int, int], color: tuple[int, int, int]):
    """
    A solid color image has zero edge variation; its sharpness score must be 0.0 and classified as BLURRY.
    """
    img = Image.new("RGB", size, color=color)
    sharpness = calculate_sharpness(img)
    assert sharpness == 0.0
    assert classify_sharpness(sharpness) == "BLURRY"


# 6. evaluate_image consistency properties
@given(size=eval_img_size_strategy, color=rgb_color_strategy)
def test_prop_evaluate_image_assessment_invariant(size: tuple[int, int], color: tuple[int, int, int]):
    """
    evaluate_image assessment must be EVIDENCE_REQUIRES_RETAKE iff warnings are present.
    """
    img = Image.new("RGB", size, color=color)
    result = evaluate_image(img)
    expected_assessment = "EVIDENCE_REQUIRES_RETAKE" if result["warnings"] else "EVIDENCE_VALID"
    assert result["assessment"] == expected_assessment


@given(size=eval_img_size_strategy, color=rgb_color_strategy)
def test_prop_evaluate_image_dimension_invariant(size: tuple[int, int], color: tuple[int, int, int]):
    """
    evaluate_image sets LOW_RESOLUTION warning iff image dimensions fail threshold validation.
    """
    img = Image.new("RGB", size, color=color)
    result = evaluate_image(img)
    is_valid_dim, expected_w, expected_h = validate_dimensions(img)

    assert result["metrics"]["width"] == expected_w
    assert result["metrics"]["height"] == expected_h
    assert ("LOW_RESOLUTION" in result["warnings"]) == (not is_valid_dim)


@given(size=eval_img_size_strategy, color=rgb_color_strategy)
def test_prop_evaluate_image_luminance_invariant(size: tuple[int, int], color: tuple[int, int, int]):
    """
    evaluate_image luminance warnings match non-optimal status.
    """
    img = Image.new("RGB", size, color=color)
    result = evaluate_image(img)
    status = result["metrics"]["luminance_status"]
    warnings = result["warnings"]

    assert ("UNDEREXPOSED" in warnings) == (status == "UNDEREXPOSED")
    assert ("OVEREXPOSED" in warnings) == (status == "OVEREXPOSED")


@given(size=eval_img_size_strategy, color=rgb_color_strategy)
def test_prop_evaluate_image_sharpness_invariant(size: tuple[int, int], color: tuple[int, int, int]):
    """
    evaluate_image sets BLURRY_IMAGE warning iff sharpness classification is BLURRY.
    """
    img = Image.new("RGB", size, color=color)
    result = evaluate_image(img)

    assert ("BLURRY_IMAGE" in result["warnings"]) == (result["metrics"]["sharpness_status"] == "BLURRY")
