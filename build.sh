#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---- Config ----------------------------------------------------------------

BINARY="${OUTPUT:-solvik}"
VERSION="${VERSION:-}"
DISTDIR="${SCRIPT_DIR}/dist"

# Detect version from git if not set
if [ -z "$VERSION" ]; then
    if git describe --tags --always --dirty 2>/dev/null; then
        VERSION="$(git describe --tags --always --dirty 2>/dev/null)"
    elif git rev-parse --short HEAD 2>/dev/null; then
        VERSION="$(git rev-parse --short HEAD 2>/dev/null)"
    else
        VERSION="dev"
    fi
fi

# ---- Functions -------------------------------------------------------------

header() {
    echo ""
    echo "============================================"
    echo "  $1"
    echo "============================================"
}

do_build() {
    header "Building"
    mkdir -p "$DISTDIR"
    echo "  Version: ${VERSION}"
    echo "  Binary:  ${DISTDIR}/${BINARY}"

    echo "  Formatting Go source files..."
    gofmt -w . 2>/dev/null || true

    go build -ldflags="-s -w" -o "${DISTDIR}/${BINARY}" ./cmd/solvik

    echo "  Binary size: $(ls -lh "${DISTDIR}/${BINARY}" | awk '{print $5}')"
}

do_test() {
    header "Running Go Tests"
    go test -count=1 ./... 2>&1
    echo ""

    if command -v cc &>/dev/null || [ "${CGO_ENABLED:-0}" = "1" ]; then
        echo "--- go test -race ---"
        CGO_ENABLED=1 go test -race -count=1 ./... 2>&1
        echo ""
    fi

    echo "--- go vet ---"
    go vet ./... 2>&1
    echo ""

    echo "--- gofmt check ---"
    UNFORMATTED="$(gofmt -l . 2>/dev/null || true)"
    if [ -n "$UNFORMATTED" ]; then
        echo "  Error: unformatted files:"
        echo "$UNFORMATTED" | sed 's/^/    /'
        exit 1
    fi
    echo "  All files formatted correctly."
}

do_scripts() {
    header "Running Script Tests"

    SCRIPTS_DIR="${SCRIPT_DIR}/test"
    if [ ! -d "$SCRIPTS_DIR" ]; then
        echo "  WARNING: test scripts directory not found at $SCRIPTS_DIR"
        return
    fi

    SCRIPT_COUNT=0
    PASS_COUNT=0
    FAIL_COUNT=0
    FAILED_SCRIPTS=""

    for script in "$SCRIPTS_DIR"/*.sol; do
        [ -f "$script" ] || continue
        relpath="./${script#$SCRIPT_DIR/}"

        # Skip helper modules that don't have a main() function
        if ! grep -q 'func main()' "$script" 2>/dev/null; then
            echo "  SKIP: $relpath (no main function)"
            continue
        fi

        SCRIPT_COUNT=$((SCRIPT_COUNT + 1))

        if "${DISTDIR}/${BINARY}" "$script" >/dev/null 2>&1; then
            echo "  PASS: $relpath"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo "  FAIL: $relpath"
            "${DISTDIR}/${BINARY}" "$script" 2>&1 || true
            FAIL_COUNT=$((FAIL_COUNT + 1))
            FAILED_SCRIPTS="$FAILED_SCRIPTS $relpath"
        fi
    done

    echo ""
    echo "  Script results: $PASS_COUNT passed, $FAIL_COUNT failed out of $SCRIPT_COUNT"
    if [ "$FAIL_COUNT" -gt 0 ]; then
        echo "  Failed scripts:$FAILED_SCRIPTS"
        exit 1
    fi
}

do_example() {
    header "Testing example.sol"

    EXAMPLE="${SCRIPT_DIR}/example.sol"
    if [ ! -f "$EXAMPLE" ]; then
        echo "  WARNING: example.sol not found at $EXAMPLE"
        return
    fi

    echo "  Running: ${DISTDIR}/${BINARY} example.sol"
    if "${DISTDIR}/${BINARY}" "$EXAMPLE" >/dev/null 2>&1; then
        echo "  PASS: example.sol"
    else
        echo "  FAIL: example.sol"
        "${DISTDIR}/${BINARY}" "$EXAMPLE" 2>&1 || true
        exit 1
    fi
}

do_dist() {
    header "Packaging Distribution"

    # Copy additional files
    cp -f "${SCRIPT_DIR}/README.md" "${DISTDIR}/" 2>/dev/null || true
    cp -f "${SCRIPT_DIR}/LICENSE" "${DISTDIR}/" 2>/dev/null || true
    cp -f "${SCRIPT_DIR}/LANGUAGE.md" "${DISTDIR}/" 2>/dev/null || true
    cp -f "${SCRIPT_DIR}/PHASE_PLAN.md" "${DISTDIR}/" 2>/dev/null || true
    echo "${VERSION}" > "${DISTDIR}/VERSION"

    echo "  Dist contents:"
    ls -la "${DISTDIR}/"
}

clean() {
    header "Cleaning"
    rm -rf "$DISTDIR"
    echo "  removed ${DISTDIR}"
}

usage() {
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  all              Multi-architecture build via goreleaser"
    echo "  native           Native build, test, and package (requires goreleaser)"
    echo "  quick            Clean, build, and package (no tests)"
    echo "  test             Run Go tests only"
    echo "  scripts          Run all test scripts in test/"
    echo "  example          Test example.sol"
    echo "  clean            Remove dist/ directory"
    echo "  release-snapshot Build snapshot release via goreleaser"
    echo "  release          Build production release via goreleaser"
    exit 1
}

# ---- Main ------------------------------------------------------------------

if [ $# -eq 0 ]; then
    clean
    do_build
    do_test
    do_scripts
    do_example
    do_dist
    header "Build complete: ${DISTDIR}/${BINARY}"
    exit 0
fi

case "${1}" in
    clean)
        clean
        ;;
    quick)
        clean
        do_build
        do_dist
        header "Quick build complete: ${DISTDIR}/${BINARY}"
        ;;
    test)
        do_test
        header "All Go tests passed."
        ;;
    scripts)
        do_scripts
        header "All script tests passed."
        ;;
    example)
        do_build
        do_example
        header "example.sol test passed."
        ;;
    all)
        header "Multi-Architecture Build"
        if command -v goreleaser &>/dev/null; then
            goreleaser release --snapshot --clean

            # Test the native binary after build
            NATIVE_BINARY=""
            if [ -f "${DISTDIR}/solvik_linux_amd64_v1/solvik" ]; then
                NATIVE_BINARY="${DISTDIR}/solvik_linux_amd64_v1/solvik"
            elif [ -f "${DISTDIR}/solvik_linux_arm64_v8.0/solvik" ]; then
                NATIVE_BINARY="${DISTDIR}/solvik_linux_arm64_v8.0/solvik"
            fi

            if [ -n "$NATIVE_BINARY" ]; then
                BINARY_SAVE="${BINARY}"
                BINARY="$(basename "$NATIVE_BINARY")"
                DISTDIR_SAVE="${DISTDIR}"
                DISTDIR="$(dirname "$NATIVE_BINARY")"
                do_scripts
                do_example
                BINARY="${BINARY_SAVE}"
                DISTDIR="${DISTDIR_SAVE}"
            fi

            header "All-architecture build complete. Artifacts in ./dist/"
        else
            echo "  goreleaser not found. Install GoReleaser or use './build.sh native'"
            echo "  See: https://goreleaser.com/install/"
            exit 1
        fi
        ;;
    native)
        clean
        do_build
        do_test
        do_scripts
        do_example
        do_dist
        header "Native build complete: ${DISTDIR}/${BINARY}"
        ;;
    release-snapshot)
        header "GoReleaser Snapshot"
        goreleaser release --snapshot --clean
        header "Snapshot complete. Artifacts in ./dist/"
        ;;
    release)
        header "GoReleaser Release"
        goreleaser release --clean
        header "Release complete."
        ;;
    *)
        usage
        ;;
esac
