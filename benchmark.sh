#!/bin/bash
# benchmark.sh — build the Go and Rust solvik interpreters and benchmark them
# against benchmark.sol.
#
# Usage:
#   ./benchmark.sh                Build both interpreters and run the benchmark
#   ./benchmark.sh --no-build     Skip the build step (reuse existing binaries)
#   ./benchmark.sh --runs 100    Run each interpreter 1000 times (default: 100)
#   ./benchmark.sh --verbose      Print per-run timings
#
# The benchmark file is ./benchmark.sol and can be overridden with BENCH_FILE.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

GO_BIN="${GO_BIN:-${SCRIPT_DIR}/dist/go/solvik}"
RUST_BIN="${RUST_BIN:-${SCRIPT_DIR}/dist/rust/solvik}"
BENCH_FILE="${BENCH_FILE:-${SCRIPT_DIR}/benchmark.sol}"

RUNS="${RUNS:-100}"
SKIP_BUILD=0
VERBOSE=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

header() {
    echo ""
    echo "============================================"
    echo "  $1"
    echo "============================================"
}

usage() {
    cat <<'EOF'
Usage: ./benchmark.sh [options]

Options:
  --no-build             Skip running ./build.sh (use existing binaries)
  --runs N               Number of timed runs per interpreter (default: 1000)
  --verbose, -v          Print per-run timings
  --help, -h             Show this help

Environment:
  RUNS                   Same as --runs
  BENCH_FILE             Benchmark source file (default: ./benchmark.sol)
  GO_BIN                 Path to the Go interpreter
  RUST_BIN               Path to the Rust interpreter
EOF
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --no-build|--skip-build)
                SKIP_BUILD=1
                shift
                ;;
            --runs)
                RUNS="${2:?--runs requires a value}"
                shift 2
                ;;
            --runs=*)
                RUNS="${1#*=}"
                shift
                ;;
            --verbose|-v)
                VERBOSE=1
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                echo "error: unknown argument: $1" >&2
                usage
                exit 1
                ;;
        esac
    done

    if ! [[ "$RUNS" =~ ^[0-9]+$ ]] || [ "$RUNS" -lt 1 ]; then
        echo "error: --runs must be a positive integer" >&2
        exit 1
    fi
}

# now_ns prints the current time in nanoseconds. It prefers GNU date and falls
# back to python3 on platforms where `date +%s%N` is unavailable (e.g. macOS).
if date +%s%N >/dev/null 2>&1; then
    now_ns() { date +%s%N; }
elif command -v python3 >/dev/null 2>&1; then
    now_ns() { python3 -c 'import time; print(time.perf_counter_ns())'; }
else
    echo "error: need GNU date (for +%s%N) or python3 for timing" >&2
    exit 1
fi

# time_run <binary> <file> prints "<nanoseconds> <exit-code>".
time_run() {
    local bin="$1" file="$2" start end code ns
    start="$(now_ns)"
    set +e
    "$bin" "$file" >/dev/null 2>&1
    code=$?
    set -e
    end="$(now_ns)"
    ns=$((end - start))
    printf '%d %d\n' "$ns" "$code"
}

# compute_stats prints "mean median min max" (in milliseconds) for the
# nanosecond values passed as arguments.
compute_stats() {
    local values=("$@")
    if [ "${#values[@]}" -eq 0 ]; then
        printf '0.000 0.000 0.000 0.000\n'
        return
    fi

    local mean median min max
    mean="$(printf '%s\n' "${values[@]}" | awk '{s += $1; n++} END {printf "%.3f", s / n / 1000000}')"
    median="$(printf '%s\n' "${values[@]}" | sort -n | awk '{a[NR] = $1} END {if (NR % 2) printf "%.3f", a[(NR + 1) / 2] / 1000000; else printf "%.3f", (a[NR / 2] + a[NR / 2 + 1]) / 2 / 1000000}')"
    min="$(printf '%s\n' "${values[@]}" | sort -n | head -1 | awk '{printf "%.3f", $1 / 1000000}')"
    max="$(printf '%s\n' "${values[@]}" | sort -n | tail -1 | awk '{printf "%.3f", $1 / 1000000}')"
    printf '%s %s %s %s\n' "$mean" "$median" "$min" "$max"
}

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

do_build() {
    if [ "$SKIP_BUILD" -eq 1 ]; then
        echo "Skipping build (--no-build)."
    else
        header "Building Go and Rust interpreters (./build.sh)"
        ./build.sh
    fi

    if [ ! -x "$GO_BIN" ]; then
        echo "error: Go interpreter not found or not executable: $GO_BIN" >&2
        echo "       run ./build.sh first" >&2
        exit 1
    fi
    if [ ! -x "$RUST_BIN" ]; then
        echo "error: Rust interpreter not found or not executable: $RUST_BIN" >&2
        echo "       run ./build.sh first" >&2
        exit 1
    fi
    if [ ! -f "$BENCH_FILE" ]; then
        echo "error: benchmark file not found: $BENCH_FILE" >&2
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

validate_benchmark() {
    header "Validating benchmark.sol"

    local go_out rust_out go_code rust_code
    set +e
    go_out="$("$GO_BIN" "$BENCH_FILE" 2>&1)"
    go_code=$?
    rust_out="$("$RUST_BIN" "$BENCH_FILE" 2>&1)"
    rust_code=$?
    set -e

    if [ "$go_code" -ne 0 ]; then
        echo "error: Go interpreter failed (exit $go_code):" >&2
        echo "$go_out" >&2
        exit 1
    fi
    if [ "$rust_code" -ne 0 ]; then
        echo "error: Rust interpreter failed (exit $rust_code):" >&2
        echo "$rust_out" >&2
        exit 1
    fi

    if [ "$go_out" != "$rust_out" ]; then
        echo "error: Go and Rust produced different output for $BENCH_FILE" >&2
        echo "--- Go ---" >&2
        echo "$go_out" >&2
        echo "--- Rust ---" >&2
        echo "$rust_out" >&2
        exit 1
    fi

    echo "  Both interpreters ran $BENCH_FILE successfully with identical output."
}

# ---------------------------------------------------------------------------
# Benchmark
# ---------------------------------------------------------------------------

run_benchmark() {
    header "Benchmarking"

    local i ns code go_times=() rust_times=()

    echo "Running $RUNS timed run(s) per interpreter..."
    for ((i = 1; i <= RUNS; i++)); do
        read -r ns code <<< "$(time_run "$GO_BIN" "$BENCH_FILE")"
        [ "$code" -eq 0 ] || { echo "error: Go timed run failed" >&2; exit 1; }
        go_times+=("$ns")
        if [ "$VERBOSE" -eq 1 ]; then
            printf '    go   run %2d: %s ns\n' "$i" "$ns"
        fi

        read -r ns code <<< "$(time_run "$RUST_BIN" "$BENCH_FILE")"
        [ "$code" -eq 0 ] || { echo "error: Rust timed run failed" >&2; exit 1; }
        rust_times+=("$ns")
        if [ "$VERBOSE" -eq 1 ]; then
            printf '    rust run %2d: %s ns\n' "$i" "$ns"
        fi
    done

    local go_stats rust_stats go_mean go_median go_min go_max
    local rust_mean rust_median rust_min rust_max

    go_stats="$(compute_stats "${go_times[@]}")"
    rust_stats="$(compute_stats "${rust_times[@]}")"
    read -r go_mean go_median go_min go_max <<< "$go_stats"
    read -r rust_mean rust_median rust_min rust_max <<< "$rust_stats"

    print_results \
        "$go_mean" "$go_median" "$go_min" "$go_max" \
        "$rust_mean" "$rust_median" "$rust_min" "$rust_max"
}

print_results() {
    local go_mean="$1" go_median="$2" go_min="$3" go_max="$4"
    local rust_mean="$5" rust_median="$6" rust_min="$7" rust_max="$8"

    header "Results"
    echo "  Benchmark file: ${BENCH_FILE}"
    echo "  Runs per interpreter: ${RUNS}"
    echo ""
    printf '  %-10s %12s %12s\n' "" "Go" "Rust"
    printf '  %-10s %12s %12s\n' "min" "${go_min} ms" "${rust_min} ms"
    printf '  %-10s %12s %12s\n' "mean" "${go_mean} ms" "${rust_mean} ms"
    printf '  %-10s %12s %12s\n' "median" "${go_median} ms" "${rust_median} ms"
    printf '  %-10s %12s %12s\n' "max" "${go_max} ms" "${rust_max} ms"
    echo ""

    awk -v go="$go_mean" -v rust="$rust_mean" 'BEGIN {
        if (go == 0 || rust == 0) {
            print "  Unable to compute a speedup (zero mean time)."
        } else if (go > rust) {
            printf "  Rust is %.2fx faster than Go (mean).\n", go / rust
        } else if (rust > go) {
            printf "  Go is %.2fx faster than Rust (mean).\n", rust / go
        } else {
            print "  Go and Rust have the same mean execution time."
        }
    }'
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

parse_args "$@"
do_build
validate_benchmark
run_benchmark

header "Benchmark complete"
