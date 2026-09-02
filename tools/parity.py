#!/usr/bin/env python3
"""Python-first Solvik conformance and differential parity runner.

The Python interpreter is the semantic oracle. Go and Rust are compared against
the full reference fixture suite.
"""
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[1]
PYTHON = [sys.executable, str(ROOT / "solvik.py")]
GO = ROOT / "dist/go/solvik"
RUST = ROOT / "dist/rust/solvik"


@dataclass(frozen=True)
class Result:
    code: int
    stdout: str
    stderr: str


def run(command: list[str], fixture: pathlib.Path, check: bool = False) -> Result:
    args = [*command]
    if check:
        args.append("--check")
    args.append(str(fixture))
    p = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    return Result(p.returncode, p.stdout.replace("\r\n", "\n"), p.stderr.replace("\r\n", "\n"))


def reference_fixtures() -> list[pathlib.Path]:
    return sorted((ROOT / "test/reference").glob("*.sol"))


def reference_valid_fixtures() -> list[pathlib.Path]:
    # Python-reference compile-only fixtures included in the full Go parity
    # suite; Rust will consume them during its parity phase.
    directory = ROOT / "test/reference/valid"
    return sorted(directory.glob("*.sol")) if directory.is_dir() else []


def reference_invalid_fixtures() -> list[pathlib.Path]:
    # Python-reference fixtures that must fail --check with the diagnostic
    # code declared in their first comment.
    directory = ROOT / "test/reference/invalid"
    return sorted(directory.glob("*.sol")) if directory.is_dir() else []


def runtime_error_fixtures() -> list[pathlib.Path]:
    # Programs that must fail at runtime with the diagnostic code declared in
    # their first comment (E0xx).
    directory = ROOT / "test/reference/runtime_errors"
    return sorted(directory.glob("*.sol")) if directory.is_dir() else []


def shared_runtime_fixtures() -> list[pathlib.Path]:
    # Keep this deterministic. Random/time/filesystem tests are intentionally
    # excluded from exact stdout differential comparison.
    names = [
        "hello.sol",
        "list_iteration.sol",
        "semicolon.sol",
        "simple_sum.sol",
        "struct_method.sol",
        "switch_test.sol",
        "trailing_comma.sol",
        "trait_test.sol",
        "variadic_test.sol",
    ]
    return [ROOT / "test" / n for n in names if (ROOT / "test" / n).is_file()]


def expected_diagnostic(path: pathlib.Path) -> str | None:
    first = path.read_text(encoding="utf-8").splitlines()[:3]
    for line in first:
        m = re.search(r"\b([A-Z]\d{3})\b", line)
        if m:
            return m.group(1)
    return None


def check_reference() -> int:
    failures = 0
    for fixture in reference_fixtures():
        result = run(PYTHON, fixture)
        if result.code != 0:
            print(f"FAIL python reference: {fixture.relative_to(ROOT)}", file=sys.stderr)
            print(result.stderr, file=sys.stderr, end="")
            failures += 1
        else:
            print(f"PASS python reference: {fixture.relative_to(ROOT)}")

    for fixture in reference_valid_fixtures():
        result = run(PYTHON, fixture, check=True)
        if result.code != 0:
            print(f"FAIL python reference valid: {fixture.relative_to(ROOT)}", file=sys.stderr)
            print(result.stderr, file=sys.stderr, end="")
            failures += 1
        else:
            print(f"PASS python reference valid: {fixture.relative_to(ROOT)}")

    for fixture in reference_invalid_fixtures():
        expected = expected_diagnostic(fixture)
        if expected is None:
            print(f"FAIL python reference invalid: {fixture.relative_to(ROOT)} has no expected diagnostic", file=sys.stderr)
            failures += 1
            continue
        result = run(PYTHON, fixture, check=True)
        if result.code == 0 or expected not in result.stderr:
            print(f"FAIL python reference invalid: {fixture.relative_to(ROOT)} expected {expected}", file=sys.stderr)
            print(result.stderr, file=sys.stderr, end="")
            failures += 1
        else:
            print(f"PASS python reference invalid: {fixture.relative_to(ROOT)}")

    valid_dir = ROOT / "test/conformance/valid"
    if valid_dir.is_dir():
        for fixture in sorted(valid_dir.glob("*.sol")):
            result = run(PYTHON, fixture, check=True)
            if result.code != 0:
                print(f"FAIL python valid conformance: {fixture.relative_to(ROOT)}", file=sys.stderr)
                print(result.stderr, file=sys.stderr, end="")
                failures += 1

    for fixture in runtime_error_fixtures():
        expected = expected_diagnostic(fixture)
        result = run(PYTHON, fixture)
        if expected is None or result.code == 0 or expected not in result.stderr:
            print(f"FAIL python runtime error: {fixture.relative_to(ROOT)} expected {expected}", file=sys.stderr)
            print(result.stderr, file=sys.stderr, end="")
            failures += 1
        else:
            print(f"PASS python runtime error: {fixture.relative_to(ROOT)}")

    invalid_dir = ROOT / "test/conformance/invalid"
    if invalid_dir.is_dir():
        for fixture in sorted(invalid_dir.glob("*.sol")):
            expected = expected_diagnostic(fixture)
            if not expected:
                continue
            result = run(PYTHON, fixture, check=True)
            if result.code == 0 or expected not in result.stderr:
                print(f"FAIL python invalid conformance: {fixture.relative_to(ROOT)} expected {expected}", file=sys.stderr)
                print(result.stderr, file=sys.stderr, end="")
                failures += 1
    return failures


def compare_optimized(path: pathlib.Path, label: str, full: bool = False) -> int:
    """Differential comparison against the Python oracle.

    With `full`, the entire reference suite is compared:
    run fixtures, compile-only valid fixtures, invalid conformance with
    expected diagnostic codes, runtime-error fixtures with expected E-codes,
    and the shared runtime corpus. The shared corpus remains included as a
    regression subset for both optimized implementations."""
    if not path.is_file():
        print(f"SKIP {label}: {path.relative_to(ROOT)} not built")
        return 0
    failures = 0
    command = [str(path)]
    if not full:
        for fixture in shared_runtime_fixtures():
            expected = run(PYTHON, fixture)
            actual = run(command, fixture)
            if actual != expected:
                print(f"FAIL {label} parity: {fixture.relative_to(ROOT)}", file=sys.stderr)
                print(f"  python: code={expected.code} stdout={expected.stdout!r} stderr={expected.stderr!r}", file=sys.stderr)
                print(f"  {label}: code={actual.code} stdout={actual.stdout!r} stderr={actual.stderr!r}", file=sys.stderr)
                failures += 1
            else:
                print(f"PASS {label} parity: {fixture.relative_to(ROOT)}")
        return failures

    def check_result(fixture, check, want_code):
        expected = run(PYTHON, fixture, check=check)
        actual = run(command, fixture, check=check)
        if expected.code == 0:
            ok = actual == expected
        else:
            ok = actual.code != 0 and want_code in actual.stderr
        if not ok:
            print(f"FAIL {label} parity: {fixture.relative_to(ROOT)}", file=sys.stderr)
            print(f"  python: code={expected.code} stdout={expected.stdout!r} stderr={expected.stderr[:160]!r}", file=sys.stderr)
            print(f"  {label}: code={actual.code} stdout={actual.stdout!r} stderr={actual.stderr[:160]!r}", file=sys.stderr)
            return 1
        return 0

    # Run-to-completion reference fixtures: exact output + exit code.
    for fixture in reference_fixtures():
        failures += check_result(fixture, check=False, want_code=None)
    # Compile-only valid fixtures.
    for fixture in reference_valid_fixtures():
        expected = run(PYTHON, fixture, check=True)
        actual = run(command, fixture, check=True)
        if (expected.code == 0) != (actual.code == 0):
            print(f"FAIL {label} valid: {fixture.relative_to(ROOT)}", file=sys.stderr)
            print(f"  python: code={expected.code} stderr={expected.stderr[:120]!r}", file=sys.stderr)
            print(f"  {label}: code={actual.code} stderr={actual.stderr[:120]!r}", file=sys.stderr)
            failures += 1
    # Invalid conformance fixtures: expected diagnostic code.
    for directory in (ROOT / "test/reference/invalid", ROOT / "test/conformance/invalid"):
        for fixture in sorted(directory.glob("*.sol")):
            want = expected_diagnostic(fixture)
            if not want:
                continue
            failures += check_result(fixture, check=True, want_code=want)
    # Runtime-error fixtures: expected E-code at exit 2.
    for fixture in runtime_error_fixtures():
        want = expected_diagnostic(fixture)
        if not want:
            continue
        failures += check_result(fixture, check=False, want_code=want)
    # Shared deterministic runtime corpus.
    for fixture in shared_runtime_fixtures():
        expected = run(PYTHON, fixture)
        actual = run(command, fixture)
        if actual != expected:
            print(f"FAIL {label} parity: {fixture.relative_to(ROOT)}", file=sys.stderr)
            print(f"  python: code={expected.code} stdout={expected.stdout!r} stderr={expected.stderr!r}", file=sys.stderr)
            print(f"  {label}: code={actual.code} stdout={actual.stdout!r} stderr={actual.stderr!r}", file=sys.stderr)
            failures += 1
        else:
            print(f"PASS {label} parity: {fixture.relative_to(ROOT)}")
    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference-only", action="store_true")
    ap.add_argument("--optimized-if-present", action="store_true")
    args = ap.parse_args()

    failures = check_reference()
    if not args.reference_only:
        failures += compare_optimized(GO, "go", full=True)
        failures += compare_optimized(RUST, "rust", full=True)
    if failures:
        print(f"parity: {failures} failure(s)", file=sys.stderr)
        return 1
    print("parity: all selected checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
