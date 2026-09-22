#!/usr/bin/env bash
# Copyright (c) 2026-present Douglas Hoard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -uo pipefail

# test-corpus.sh runs the checked-in .sol corpus through a real Solvik distribution
# exactly the way an end user invokes it, and compares against the golden .output
# files. It is invoked by build.sh (against the JVM launcher) and build-native.sh
# (against the native-image binary) so that the shipped entry points - not just the
# in-process Context.eval API - are validated end to end.
#
# Usage: ./test-corpus.sh <path-to-solvik-executable> [extra launcher args ...]
#
# <path-to-solvik-executable> is either the JVM launcher script
# (standalone/target/solvik) or the native binary (standalone/target/solvik-native).
# Extra args are forwarded to the launcher (e.g. --engine.WarnInterpreterOnly=false).
#
# Corpus contract (mirrors SolvikProgramTest / SolvikRegressionProgramTest):
#   * language/tests/*.sol                -> must exit 0 and stdout == its .output
#   * language/tests/regression/*.sol     -> if a sibling .output exists it must exit 0
#                                            and match it; otherwise the program was
#                                            rejected by the baseline and must exit
#                                            non-zero with empty stdout.

if [[ $# -lt 1 ]]; then
    printf 'test-corpus.sh: usage: %s <path-to-solvik-executable> [extra launcher args ...]\n' "$0" >&2
    exit 2
fi

RUNNER=("$@")
exec_target="${RUNNER[0]}"

if [[ ! -e "$exec_target" ]]; then
    printf 'test-corpus.sh: executable not found: %s\n' "$exec_target" >&2
    exit 2
fi
# Maven resource copying does not always preserve the executable bit on the launcher
# script; restore it so the JVM distribution can be run directly.
if [[ -f "$exec_target" && ! -x "$exec_target" ]]; then
    chmod +x "$exec_target" || exit 2
fi

cd "$(dirname "$0")" || exit 2

examples_dir="language/tests"
regression_dir="language/tests/regression"

if [[ ! -d "$examples_dir" || ! -d "$regression_dir" ]]; then
    printf 'test-corpus.sh: corpus directories not found (run from the repository root)\n' >&2
    exit 2
fi

runner_label="${RUNNER[*]}"
failures=0
examples=0
regressions=0

# Compare guest stdout only: the launcher and JVM may print warnings to stderr
# (restricted-method and JVMCI notices) that are not part of program output.
# The guest exit status is captured for the rejection cases.
run_case() {
    local file="$1"
    local tmp_out
    tmp_out="$(mktemp)"
    local tmp_err
    tmp_err="$(mktemp)"
    local status=0
    "${RUNNER[@]}" "$file" >"$tmp_out" 2>"$tmp_err" || status=$?
    printf '__RC__=%s\n__STDOUT_FILE__=%s\n__STDERR_FILE__=%s\n' "$status" "$tmp_out" "$tmp_err"
}

# --- examples: every *.sol must succeed and match its golden on stdout ---
for file in "$examples_dir"/*.sol; do
    [[ -e "$file" ]] || continue
    golden="${file%.sol}.output"
    examples=$((examples + 1))
    output="$(run_case "$file")"
    status="$(printf '%s\n' "$output" | sed -n 's/^__RC__=//p')"
    out_file="$(printf '%s\n' "$output" | sed -n 's/^__STDOUT_FILE__=//p')"
    err_file="$(printf '%s\n' "$output" | sed -n 's/^__STDERR_FILE__=//p')"
    if [[ ! -f "$golden" ]]; then
        printf 'FAIL (missing golden %s): %s\n' "$golden" "$file" >&2
        rm -f "$out_file" "$err_file"
        failures=$((failures + 1))
        continue
    fi
    if [[ "$status" != "0" ]]; then
        printf 'FAIL (exit %s): %s\n' "$status" "$file" >&2
        sed -n '1,40p' "$err_file" >&2
        failures=$((failures + 1))
    elif ! diff -u "$golden" "$out_file" >&2; then
        printf 'FAIL (output mismatch): %s\n' "$file" >&2
        failures=$((failures + 1))
    fi
    rm -f "$out_file" "$err_file"
done

# --- regressions: golden match, or rejection with empty stdout ---
for file in "$regression_dir"/*.sol; do
    [[ -e "$file" ]] || continue
    golden="${file%.sol}.output"
    regressions=$((regressions + 1))
    output="$(run_case "$file")"
    status="$(printf '%s\n' "$output" | sed -n 's/^__RC__=//p')"
    out_file="$(printf '%s\n' "$output" | sed -n 's/^__STDOUT_FILE__=//p')"
    err_file="$(printf '%s\n' "$output" | sed -n 's/^__STDERR_FILE__=//p')"
    if [[ -f "$golden" ]]; then
        if [[ "$status" != "0" ]]; then
            printf 'FAIL (expected success, exit %s): %s\n' "$status" "$file" >&2
            sed -n '1,40p' "$err_file" >&2
            failures=$((failures + 1))
        elif ! diff -u "$golden" "$out_file" >&2; then
            printf 'FAIL (output mismatch): %s\n' "$file" >&2
            failures=$((failures + 1))
        fi
    else
        if [[ "$status" == "0" ]]; then
            printf 'FAIL (expected rejection, exited 0): %s\n' "$file" >&2
            failures=$((failures + 1))
        elif [[ -s "$out_file" ]]; then
            printf 'FAIL (rejected program produced stdout): %s\n' "$file" >&2
            failures=$((failures + 1))
        fi
    fi
    rm -f "$out_file" "$err_file"
done

if [[ "$examples" -eq 0 || "$regressions" -eq 0 ]]; then
    printf 'test-corpus.sh: empty corpus (examples=%s regressions=%s)\n' "$examples" "$regressions" >&2
    exit 1
fi

if [[ "$failures" -ne 0 ]]; then
    printf 'test-corpus.sh: %s failure(s) over %s example(s) and %s regression(s) using: %s\n' \
        "$failures" "$examples" "$regressions" "$runner_label" >&2
    exit 1
fi

printf 'test-corpus.sh: OK - %s example(s) and %s regression(s) passed using: %s\n' \
    "$examples" "$regressions" "$runner_label"
