#!/usr/bin/env python3
"""Standalone Byte-Level Mutation Fuzzing Harness for Image Processing & Analyzer.

Mutates binary image payloads using random bit flips, byte deletions, insertions,
and stream truncation, testing both Pillow image decoding resilience and downstream
quality evaluation functions in app.analyzer.
"""

import argparse
import glob
import io
import os
import random
import sys
import time
from typing import List, Optional, Tuple

from PIL import Image, UnidentifiedImageError

# Ensure app package is accessible
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.analyzer import evaluate_image


def load_seed_corpus(fixtures_dir: str) -> List[bytes]:
    """Load sample JPEG images from fixtures as seed corpus."""
    corpus = []
    pattern = os.path.join(fixtures_dir, "*.jpg")
    for filepath in glob.glob(pattern):
        with open(filepath, "rb") as f:
            corpus.append(f.read())
    if not corpus:
        # Fallback synthetic seed if fixtures directory is missing or empty
        img = Image.new("RGB", (64, 64), color=(200, 100, 50))
        buf = io.BytesIO()
        img.save(buf, format="JPEG")
        corpus.append(buf.getvalue())
    return corpus


def _apply_bit_flip(mutated: bytearray) -> None:
    idx = random.randint(0, len(mutated) - 1)
    bit = 1 << random.randint(0, 7)
    mutated[idx] ^= bit


def _apply_byte_replace(mutated: bytearray) -> None:
    idx = random.randint(0, len(mutated) - 1)
    mutated[idx] = random.randint(0, 255)


def _apply_byte_delete(mutated: bytearray) -> None:
    if len(mutated) > 10:
        idx = random.randint(0, len(mutated) - 1)
        del mutated[idx]


def _apply_byte_insert(mutated: bytearray) -> None:
    idx = random.randint(0, len(mutated))
    mutated.insert(idx, random.randint(0, 255))


def _apply_byte_truncate(mutated: bytearray) -> None:
    if len(mutated) > 20:
        cut_idx = random.randint(10, len(mutated) - 1)
        del mutated[cut_idx:]


MUTATION_OPERATORS = [
    _apply_bit_flip,
    _apply_byte_replace,
    _apply_byte_delete,
    _apply_byte_insert,
    _apply_byte_truncate,
]


def mutate_bytes(data: bytes, mutation_rate: float = 0.001) -> bytes:
    """Apply random mutations to byte array: bit flips, substitutions, deletions, insertions."""
    mutated = bytearray(data)
    rate = random.choice([0.0001, 0.0005, 0.001, 0.01, 0.05])
    num_mutations = max(1, int(len(mutated) * rate))

    for _ in range(num_mutations):
        if not mutated:
            break
        operator = random.choice(MUTATION_OPERATORS)
        operator(mutated)

    return bytes(mutated)


def _fuzz_single_iteration(payload: bytes) -> Tuple[bool, bool, Optional[Exception]]:
    """Decode and evaluate mutated payload. Returns (decoded, rejected, error)."""
    try:
        img = Image.open(io.BytesIO(payload))
        img.load()
        analysis = evaluate_image(img)
        assert analysis["assessment"] in {"EVIDENCE_VALID", "EVIDENCE_REQUIRES_RETAKE"}
        assert 0.0 <= analysis["metrics"]["luminance"] <= 255.0
        assert analysis["metrics"]["sharpness_score"] >= 0.0
        return True, False, None
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        return False, True, None
    except Exception as e:
        return False, False, e


def _report_progress(i: int, total: int, start_time: float, counts: Tuple[int, int, int]) -> None:
    """Print fuzzing throughput and progress if at reporting cadence."""
    if i % 500 != 0 and i != total:
        return
    elapsed = max(time.time() - start_time, 1e-6)
    decoded, rejected, crashes = counts
    print(f"Iter {i}/{total} ({i / elapsed:.1f} exec/s) | Decoded: {decoded} | Cleanly Rejected: {rejected} | Crashes: {crashes}")


def run_fuzz(iterations: int, fixtures_dir: str, seed: int = None) -> int:
    """Run byte mutation fuzzing iterations."""
    if seed is not None:
        random.seed(seed)

    corpus = load_seed_corpus(fixtures_dir)
    print(f"[FUZZ HARNESS] Loaded {len(corpus)} seed files from {fixtures_dir}")
    print(f"[FUZZ HARNESS] Running {iterations} iterations...")

    start_time = time.time()
    decoded_count = 0
    rejected_count = 0
    crash: Optional[Exception] = None
    i = 1

    while not crash and i <= iterations:
        seed_data = random.choice(corpus)
        mutated_data = mutate_bytes(seed_data)
        decoded, rejected, crash = _fuzz_single_iteration(mutated_data)
        decoded_count += int(decoded)
        rejected_count += int(rejected)
        _report_progress(i, iterations, start_time, (decoded_count, rejected_count, 0))
        i += 1

    if crash is not None:
        print(f"\n[CRASH DETECTED] Iteration #{i - 1}: {type(crash).__name__}: {crash}")

    elapsed = time.time() - start_time
    crashes = int(crash is not None)
    print(f"\n[FUZZ HARNESS COMPLETED] in {elapsed:.2f}s. Total Crashes: {crashes}")
    return crashes


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Image Processing Fuzz Harness")
    parser.add_argument("--iterations", type=int, default=1000, help="Number of fuzz iterations (default: 1000)")
    parser.add_argument("--fixtures", type=str, default=os.path.join(os.path.dirname(__file__), "fixtures"), help="Path to fixtures")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducibility")

    args = parser.parse_args()
    sys.exit(run_fuzz(iterations=args.iterations, fixtures_dir=args.fixtures, seed=args.seed))
