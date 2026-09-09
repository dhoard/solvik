#!/usr/bin/env bash
# Build and test the Solvik compiler and bytecode VM.
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v cargo >/dev/null; then export PATH="$HOME/.cargo/bin:$PATH"; fi
build() {
    mkdir -p dist
    cargo build --release
    cp target/release/solvik dist/solvik
}
unit() {
    cargo fmt -- --check
    cargo test
    cargo clippy --all-targets --all-features -- -D warnings
}
integration() {
    ./test/run.sh dist/solvik
}
case "${1:-all}" in
    all|test) unit; build; integration ;;
    build) build ;;
    integration) build; integration ;;
    clean) rm -rf dist target ;;
    help|-h|--help) echo 'Usage: ./build.sh [all|build|test|integration|clean]' ;;
    *) echo "Unknown command: $1" >&2; exit 1 ;;
esac
