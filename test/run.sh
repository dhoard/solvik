#!/usr/bin/env bash
# Conformance test runner for the Solvik Rust VM.
#
# Usage:
#   ./test/run.sh [path-to-solvik-binary]
#
# Each directory under test/cases/ contains:
#   main.sol        -- the program to run
#   expected.out    -- expected combined stdout+stderr (optional; empty = no output check)
#   expected.code   -- expected exit code (optional; default 0)
#   args.txt        -- program arguments, one per line (optional)
set -u
cd "$(dirname "$0")/.."
BIN="${1:-target/debug/solvik}"
if [ ! -x "$BIN" ]; then
    echo "binary not found: $BIN (build with ./build.sh build first)" >&2
    exit 1
fi
pass=0
fail=0
for d in test/cases/*/; do
    d="${d%/}"
    name="$(basename "$d")"
    want_code=0
    if [ -f "$d/expected.code" ]; then
        want_code="$(cat "$d/expected.code")"
    fi
    program_args=()
    if [ -f "$d/args.txt" ]; then
        mapfile -t program_args < "$d/args.txt"
    fi
    input="/dev/null"
    if [ -f "$d/stdin.txt" ]; then input="$d/stdin.txt"; fi
    out="$("$BIN" "$d/main.sol" "${program_args[@]}" < "$input" 2>&1)"
    code=$?
    ok=1
    if [ "$code" -ne "$want_code" ]; then
        ok=0
    fi
    if [ -f "$d/expected.out" ] && [ -n "$(cat "$d/expected.out")" ]; then
        if [ "$out" != "$(cat "$d/expected.out")" ]; then
            ok=0
        fi
    fi
    if [ "$ok" -eq 1 ]; then
        echo "ok   $name"
        pass=$((pass + 1))
    else
        echo "FAIL $name (exit=$code want=$want_code)"
        if [ -n "$out" ]; then
            printf '%s\n' "$out" | sed 's/^/     | /'
        fi
        fail=$((fail + 1))
    fi
done
echo "----------------------------------------"
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
