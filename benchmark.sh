#!/usr/bin/env bash
# Validate deterministic behavior, then benchmark the VM with monotonic time.
set -euo pipefail
cd "$(dirname "$0")"
runs="${RUNS:-100}"
verbose=false
build=true
while (($#)); do
    case "$1" in
        --no-build|--skip-build) build=false; shift ;;
        --runs) runs="${2:?--runs requires a value}"; shift 2 ;;
        --runs=*) runs="${1#*=}"; shift ;;
        --verbose|-v) verbose=true; shift ;;
        --help|-h)
            echo 'Usage: ./benchmark.sh [--no-build] [--runs N] [--verbose]'
            echo 'Environment: RUNS (100), BENCH_FILE'
            exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done
[[ "$runs" =~ ^[0-9]+$ ]] && ((runs > 0)) || { echo 'runs must be positive' >&2; exit 1; }
if "$build"; then ./build.sh build; fi
bin="dist/solvik"
file="${BENCH_FILE:-benchmark.sol}"
[[ -x "$bin" ]] || { echo "binary not found: $bin (run ./build.sh build)" >&2; exit 1; }

# Determinism check: two runs must produce identical output.
a="$("$bin" "$file")"
b="$("$bin" "$file")"
if [[ "$a" != "$b" ]]; then
    echo "benchmark output is not deterministic" >&2
    exit 1
fi
if "$verbose"; then echo "output: $a"; fi

# Monotonic timing via nanosecond clock.
start=$(date +%s%N)
for ((i = 0; i < runs; i++)); do
    out="$("$bin" "$file")"
    if [[ "$out" != "$a" ]]; then
        echo "run $i produced different output" >&2
        exit 1
    fi
done
end=$(date +%s%N)
total_ms=$(( (end - start) / 1000000 ))
avg_ms=$(( total_ms / runs ))
echo "solvik: $runs runs in ${total_ms}ms (avg ${avg_ms}ms)"
