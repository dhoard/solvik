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
        SCRIPT_COUNT=$((SCRIPT_COUNT + 1))
        relpath="./${script#$SCRIPT_DIR/}"

        if "${DISTDIR}/${BINARY}" run "$script" >/dev/null 2>&1; then
            echo "  PASS: $relpath"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo "  FAIL: $relpath"
            "${DISTDIR}/${BINARY}" run "$script" 2>&1 || true
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

do_dist() {
    header "Packaging Distribution"

    # Copy additional files
    cp -f "${SCRIPT_DIR}/README.md" "${DISTDIR}/" 2>/dev/null || true
    cp -f "${SCRIPT_DIR}/LICENSE" "${DISTDIR}/" 2>/dev/null || true
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

# ---- Main ------------------------------------------------------------------

case "${1:-all}" in
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
    all|"")
        header "Multi-Architecture Build"
        if command -v goreleaser &>/dev/null; then
            goreleaser release --snapshot --clean
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
        do_dist
        do_scripts
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
        echo "Usage: $0 [all|native|quick|test|scripts|clean|release-snapshot|release]"
        exit 1
        ;;
esac
