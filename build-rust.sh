#!/bin/bash
# build-rust.sh — build and test the Rust implementation of the solvik
# interpreter.
#
# The Rust interpreter lives in ./rust (a Cargo crate). This script:
#   1. Builds the Rust binary (release) into ./dist/rust/solvik
#   2. Runs the Rust unit tests (cargo test)
#   3. Runs every integration script (test/*.sol, test/conformance/*) with the
#      Rust binary and verifies expected exit codes / diagnostic codes
#   4. When the Go toolchain is available, builds the reference Go binary into
#      ./dist/go/solvik and differential-tests Go, Rust, and Python. Deterministic
#      output, exit codes, and complete invalid-fixture diagnostics must match.
#
# Existing Go code is untouched; only ./dist/rust/solvik and
# ./dist/go/solvik (the differential reference) are written.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

DISTDIR="${SCRIPT_DIR}/dist"
RUST_DIR="${SCRIPT_DIR}/rust"
RUST_BIN="${DISTDIR}/rust/solvik"
GO_BIN="${DISTDIR}/go/solvik"
PY_BIN="${SCRIPT_DIR}/solvik.py"
SCRIPTS_DIR="${SCRIPT_DIR}/test"

# Scripts whose stdout is nondeterministic (time, random, temp paths, secrets).
# For these we only compare exit codes, not stdout.
NONDETERMINISTIC=(
    "file_temp_test.sol"
    "secrets_test.sol"
    "random_test.sol"
    "example.sol"
)

header() {
    echo "============================================"
    echo "  $1"
    echo "============================================"
    echo ""
}

need_cargo() {
    if ! command -v cargo &>/dev/null; then
        if [ -x "$HOME/.cargo/bin/cargo" ]; then
            export PATH="$HOME/.cargo/bin:$PATH"
        else
            echo "  ERROR: cargo not found. Install Rust: https://rustup.rs/"
            exit 1
        fi
    fi
}

do_build() {
    header "Building Rust interpreter"
    need_cargo
    mkdir -p "${DISTDIR}/rust"
    cargo build --release --manifest-path "$RUST_DIR/Cargo.toml"
    cp -f "$RUST_DIR/target/release/solvik" "$RUST_BIN"
    echo "  Built: ${RUST_BIN}"
    ls -lh "$RUST_BIN" | awk '{print "  Size: " $5}'
    echo ""
}

do_unit_tests() {
    header "Running Rust unit tests"
    need_cargo
    cargo test --release --manifest-path "$RUST_DIR/Cargo.toml"
}

do_build_go_reference() {
    # Build the reference Go binary only if the toolchain is available.
    if command -v go &>/dev/null; then
        header "Building reference Go binary"
        mkdir -p "${DISTDIR}/go"
        go build -o "$GO_BIN" ./cmd/solvik
        echo "  Built: ${GO_BIN}"
        echo ""
        return 0
    fi
    return 1
}

is_nondeterministic() {
    local base
    base="$(basename "$1")"
    for n in "${NONDETERMINISTIC[@]}"; do
        if [ "$base" = "$n" ]; then
            return 0
        fi
    done
    return 1
}

do_integration() {
    header "Running integration scripts"

    local total=0 pass=0 fail=0
    local failed_list=""
    local file base

    for file in "$SCRIPTS_DIR"/*.sol; do
        [ -f "$file" ] || continue
        base="$(basename "$file")"

        # Skip helper modules without a main function
        if ! grep -q 'func main()' "$file" 2>/dev/null; then
            continue
        fi

        total=$((total + 1))

        # The Rust interpreter must succeed on its own.
        if "$RUST_BIN" "$file" >/dev/null 2>&1; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            failed_list="$failed_list $base"
            echo "  FAIL(rust): $file"
            "$RUST_BIN" "$file" 2>&1 | sed 's/^/    /' | head -8
        fi
    done

    # example.sol (the comprehensive demo)
    total=$((total + 1))
    if "$RUST_BIN" example.sol >/dev/null 2>&1; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        failed_list="$failed_list example.sol"
        echo "  FAIL(rust): example.sol"
    fi

    echo "  Rust-only results: $pass passed, $fail failed out of $total"
    if [ "$fail" -gt 0 ]; then
        echo "  Failed:$failed_list"
        exit 1
    fi
    echo ""
}

do_conformance() {
    header "Running conformance fixtures"

    local total=0 pass=0 fail=0
    local f want code

    # Valid fixtures: the Rust binary must compile and run them (any exit code
    # is acceptable — the Go toolchain treats them as compile-only fixtures).
    for f in "$SCRIPTS_DIR"/conformance/valid/*.sol; do
        [ -f "$f" ] || continue
        total=$((total + 1))
        if "$RUST_BIN" "$f" >/dev/null 2>&1; then
            pass=$((pass + 1))
        else
            # A runtime error (nonzero exit) may still mean the fixture
            # compiled; treat nonzero exit as success only when the stderr has
            # no compiler diagnostics. Compiler diagnostics always print
            # "error <CODE>:" lines.
            if "$RUST_BIN" "$f" 2>&1 | grep -qE '^error [A-Z][0-9]+:'; then
                fail=$((fail + 1))
                echo "  FAIL: $f"
            else
                pass=$((pass + 1))
            fi
        fi
    done

    # Invalid fixtures: the Rust binary must produce the expected diagnostic.
    for f in "$SCRIPTS_DIR"/conformance/invalid/*.sol; do
        [ -f "$f" ] || continue
        want="$(head -1 "$f" | sed 's|// expect: ||' | tr -d '[:space:]' || true)"
        total=$((total + 1))
        code="$("$RUST_BIN" "$f" 2>&1 | grep -oE '^error [A-Z][0-9]+' | head -1 | awk '{print $2}' || true)"
        if [ "$code" = "$want" ]; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            echo "  FAIL: $f (want $want, got ${code:-none})"
        fi
    done

    echo "  Conformance results: $pass passed, $fail failed out of $total"
    if [ "$fail" -gt 0 ]; then
        exit 1
    fi
    echo ""
}

do_differential() {
    if ! command -v go &>/dev/null; then
        header "Differential tests (skipped — no Go toolchain)"
        return
    fi

    if [ ! -x "$PY_BIN" ]; then
        echo "  Python reference interpreter is not executable: $PY_BIN"
        exit 1
    fi

    header "Differential tests (Go vs Rust vs Python)"

    local total=0 pass=0 fail=0
    local f base go_code rs_code py_code

    for f in "$SCRIPTS_DIR"/*.sol; do
        [ -f "$f" ] || continue
        base="$(basename "$f")"
        if ! grep -q 'func main()' "$f" 2>/dev/null; then
            continue
        fi

        total=$((total + 1))

        set +e
        "$GO_BIN" "$f" >/tmp/go.out 2>/tmp/go.err; go_code=$?
        "$RUST_BIN" "$f" >/tmp/rs.out 2>/tmp/rs.err; rs_code=$?
        "$PY_BIN" "$f" >/tmp/py.out 2>/tmp/py.err; py_code=$?
        set -e

        if [ "$go_code" != "$rs_code" ] || [ "$go_code" != "$py_code" ]; then
            fail=$((fail + 1))
            echo "  FAIL(exit): $base (go=$go_code rust=$rs_code python=$py_code)"
            continue
        fi

        if is_nondeterministic "$f"; then
            # Only compare exit codes for nondeterministic scripts.
            pass=$((pass + 1))
            continue
        fi

        if diff -q /tmp/go.out /tmp/rs.out >/dev/null \
            && diff -q /tmp/go.out /tmp/py.out >/dev/null \
            && diff -q /tmp/go.err /tmp/rs.err >/dev/null \
            && diff -q /tmp/go.err /tmp/py.err >/dev/null; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            echo "  FAIL(output): $base"
            diff -u /tmp/go.out /tmp/rs.out | head -8 | sed 's/^/    rust: /' || true
            diff -u /tmp/go.out /tmp/py.out | head -8 | sed 's/^/    python: /' || true
            diff -u /tmp/go.err /tmp/rs.err | head -8 | sed 's/^/    rust stderr: /' || true
            diff -u /tmp/go.err /tmp/py.err | head -8 | sed 's/^/    python stderr: /' || true
        fi
    done

    # example.sol — compare only exit code (nondeterministic stdout).
    total=$((total + 1))
    set +e
    "$GO_BIN" example.sol >/dev/null 2>&1; go_code=$?
    "$RUST_BIN" example.sol >/dev/null 2>&1; rs_code=$?
    "$PY_BIN" example.sol >/dev/null 2>&1; py_code=$?
    set -e
    if [ "$go_code" = "$rs_code" ] && [ "$go_code" = "$py_code" ]; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        echo "  FAIL(exit): example.sol (go=$go_code rust=$rs_code python=$py_code)"
    fi

    # Conformance valid fixtures: all observable output and exit status match.
    for f in "$SCRIPTS_DIR"/conformance/valid/*.sol; do
        [ -f "$f" ] || continue
        total=$((total + 1))
        set +e
        "$GO_BIN" "$f" >/tmp/go.out 2>/tmp/go.err; go_code=$?
        "$RUST_BIN" "$f" >/tmp/rs.out 2>/tmp/rs.err; rs_code=$?
        "$PY_BIN" "$f" >/tmp/py.out 2>/tmp/py.err; py_code=$?
        set -e
        if [ "$go_code" = "$rs_code" ] && [ "$go_code" = "$py_code" ] \
            && diff -q /tmp/go.out /tmp/rs.out >/dev/null \
            && diff -q /tmp/go.out /tmp/py.out >/dev/null \
            && diff -q /tmp/go.err /tmp/rs.err >/dev/null \
            && diff -q /tmp/go.err /tmp/py.err >/dev/null; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            echo "  FAIL: $(basename "$f") (go=$go_code rust=$rs_code python=$py_code)"
        fi
    done

    # Invalid fixtures: exit status and complete rendered diagnostics must match.
    for f in "$SCRIPTS_DIR"/conformance/invalid/*.sol; do
        [ -f "$f" ] || continue
        total=$((total + 1))
        set +e
        "$GO_BIN" "$f" >/tmp/go.out 2>/tmp/go.err; go_code=$?
        "$RUST_BIN" "$f" >/tmp/rs.out 2>/tmp/rs.err; rs_code=$?
        "$PY_BIN" "$f" >/tmp/py.out 2>/tmp/py.err; py_code=$?
        set -e
        if [ "$go_code" = "$rs_code" ] && [ "$go_code" = "$py_code" ] \
            && diff -q /tmp/go.out /tmp/rs.out >/dev/null \
            && diff -q /tmp/go.out /tmp/py.out >/dev/null \
            && diff -q /tmp/go.err /tmp/rs.err >/dev/null \
            && diff -q /tmp/go.err /tmp/py.err >/dev/null; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            echo "  FAIL(diagnostic): $(basename "$f") (go=$go_code rust=$rs_code python=$py_code)"
            diff -u /tmp/go.err /tmp/rs.err | head -8 | sed 's/^/    rust: /' || true
            diff -u /tmp/go.err /tmp/py.err | head -8 | sed 's/^/    python: /' || true
        fi
    done

    echo "  Three-way differential results: $pass matched, $fail mismatched out of $total"
    if [ "$fail" -gt 0 ]; then
        exit 1
    fi
    echo ""
}

usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands (default: all):"
    echo "  all            Build, unit test, integration test, differential test"
    echo "  build          Build the Rust binary only"
    echo "  test           Build + unit tests + integration tests (no differential)"
    echo "  integration    Build + run integration scripts"
    echo "  differential   Build + three-way differential test using Go as reference"
    exit 1
}

case "${1:-all}" in
    all)
        do_build
        do_unit_tests
        do_integration
        do_conformance
        do_build_go_reference && do_differential
        header "Rust build complete: ${RUST_BIN}"
        ;;
    build)
        do_build
        ;;
    test)
        do_build
        do_unit_tests
        do_integration
        do_conformance
        header "Rust tests passed."
        ;;
    integration)
        do_build
        do_integration
        do_conformance
        header "Rust integration tests passed."
        ;;
    differential)
        do_build
        do_build_go_reference || { echo "  Go toolchain required for differential tests"; exit 1; }
        do_differential
        header "Differential tests passed."
        ;;
    *)
        usage
        ;;
esac
