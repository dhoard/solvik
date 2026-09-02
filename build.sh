#!/bin/bash
# build.sh — umbrella build for the solvik toolchain.
#
# Delegates to the language-specific build scripts:
#   ./build-go.sh   — Go implementation   (artifacts in ./dist/go/)
#   ./build-rust.sh — Rust implementation (artifacts in ./dist/rust/)
#
# Usage:
#   ./build.sh                    Build and test all three implementations
#   ./build.sh go [args...]       Run the Go build:    ./build-go.sh [args...]
#   ./build.sh rust [args...]     Run the Rust build:  ./build-rust.sh [args...]
#   ./build.sh clean              Remove ./dist/go/ and ./dist/rust/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

header() {
    echo "============================================"
    echo "  $1"
    echo "============================================"
    echo ""
}

usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  (no command) / all   Build and test Python, Go, and Rust"
    echo "  go <args...>         Run ./build-go.sh with the given arguments"
    echo "  rust <args...>       Run ./build-rust.sh with the given arguments"
    echo "  clean                Remove ./dist/go/ and ./dist/rust/"
    echo "  help                 Show this help"
    exit 1
}

do_python() {
    header "solvik: Python semantic reference"
    python3 -m py_compile ./solvik.py
    python3 ./tools/parity.py --reference-only

    local total=0 pass=0 fail=0
    local file base failed_list=""
    for file in "${SCRIPT_DIR}/test"/*.sol; do
        [ -f "$file" ] || continue
        if ! grep -q 'func main()' "$file" 2>/dev/null; then
            continue
        fi
        total=$((total + 1))
        if python3 "$SCRIPT_DIR/solvik.py" "$file" >/dev/null 2>&1; then
            pass=$((pass + 1))
        else
            fail=$((fail + 1))
            base="$(basename "$file")"
            failed_list="$failed_list $base"
            echo "  FAIL(python): $file"
            python3 "$SCRIPT_DIR/solvik.py" "$file" 2>&1 | sed 's/^/    /' | head -8
        fi
    done

    if python3 "$SCRIPT_DIR/solvik.py" "$SCRIPT_DIR/example.sol" >/dev/null 2>&1; then
        pass=$((pass + 1))
        echo "  Python example: passed"
    else
        fail=$((fail + 1))
        failed_list="$failed_list example.sol"
        echo "  FAIL(python): $SCRIPT_DIR/example.sol"
        python3 "$SCRIPT_DIR/solvik.py" "$SCRIPT_DIR/example.sol" 2>&1 | sed 's/^/    /' | head -8
    fi

    echo "  Python runtime results: $pass passed, $fail failed out of $((total + 1))"
    if [ "$fail" -gt 0 ]; then
        echo "  Failed:$failed_list"
        exit 1
    fi
    echo ""
}

do_all() {
    do_python

    header "solvik: Go toolchain"
    ./build-go.sh

    header "solvik: Rust toolchain"
    ./build-rust.sh all

    header "solvik: differential parity"
    python3 ./tools/parity.py --optimized-if-present

    header "Build complete"
    echo "  Python reference: ./solvik.py"
    echo "  Go:               ./dist/go/solvik"
    echo "  Rust:             ./dist/rust/solvik"
}

case "${1:-all}" in
    all)
        do_all
        ;;
    go)
        shift
        ./build-go.sh "$@"
        ;;
    rust)
        shift
        ./build-rust.sh "$@"
        ;;
    clean)
        ./build-go.sh clean
        rm -rf "${SCRIPT_DIR}/dist/rust"
        echo "  removed ./dist/go/ and ./dist/rust/"
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        usage
        ;;
esac
