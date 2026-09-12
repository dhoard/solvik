#!/usr/bin/env bash
# Run the Solvik performance benchmark suite (benches/bench.rs).
#
# Usage:
#   ./benchmark.sh                    # full suite: compile, workloads, micro
#   ./benchmark.sh <filter>           # only entries whose name contains <filter>
#   ./benchmark.sh stages             # per-pipeline-stage report
#   ./benchmark.sh sizes              # value/frame sizes per workload
#   ./benchmark.sh verify             # verifier-only report
#   ./benchmark.sh micro              # microbenchmarks only
#   ./benchmark.sh --alloc            # allocation accounting (feature bench-alloc)
#   ./benchmark.sh --alloc <filter>
#
# Examples:
#   ./benchmark.sh int_loop
#   ./benchmark.sh compile
#   ./benchmark.sh --alloc strings
set -euo pipefail
cd "$(dirname "$0")"

if [[ "${1:-}" == "--alloc" ]]; then
    shift
    exec cargo bench --features bench-alloc -- "$@"
fi

exec cargo bench -- "$@"
