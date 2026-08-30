#!/bin/bash
# build.sh — umbrella build for the solvik toolchain.
#
# Delegates to the language-specific build scripts:
#   ./build-go.sh   — Go implementation   (artifacts in ./dist/go/)
#   ./build-rust.sh — Rust implementation (artifacts in ./dist/rust/)
#
# Usage:
#   ./build.sh                    Build and test both implementations
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
    echo "  (no command) / all   Build and test both Go and Rust"
    echo "  go <args...>         Run ./build-go.sh with the given arguments"
    echo "  rust <args...>       Run ./build-rust.sh with the given arguments"
    echo "  clean                Remove ./dist/go/ and ./dist/rust/"
    echo "  help                 Show this help"
    exit 1
}

do_all() {
    header "solvik: Python semantic reference"
    python3 -m py_compile ./solvik.py
    python3 ./tools/parity.py --reference-only

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
