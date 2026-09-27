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
set -euo pipefail

# Solvik TCK differential run: the same portable programs through two adapters
# (TCK.md section 15).
#
# The valuable pair is the two shipped distributions -- JVM and native. They implement
# one specification from one source tree, so a disagreement between them is a defect in
# this repository and is a hard build failure, regardless of what any oracle says.
#
# What this is NOT: a conformance verdict. Agreement between implementations is
# evidence and never proof -- two builds can share a bug, and this run can only compare
# programs the corpus contains. It supplements the conformance runs (tck-run.sh), which
# remain the sole source of PASS/FAIL against the normative oracle, and it can neither
# update an expected result nor override one.
#
# Usage (from anywhere): ./tck/tck-differential.sh <left-exe> <left-name> <right-exe> <right-name>

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"

left_exe="${1:-}"; left_name="${2:-}"; right_exe="${3:-}"; right_name="${4:-}"
if [[ -z "$left_exe" || -z "$left_name" || -z "$right_exe" || -z "$right_name" ]]; then
    printf 'usage: %s <left-exe> <left-name> <right-exe> <right-name>\n' "$0" >&2
    exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
    printf 'tck-differential.sh: python3 is required for the portable TCK runner.\n' >&2
    exit 2
fi

report_dir="$root/tck/reports"
report="$report_dir/differential-$left_name-$right_name.json"
mkdir -p "$report_dir"

abs() {
    local exe="$1"
    if [[ ! -x "$exe" && ! -f "$exe" ]]; then
        printf 'tck-differential.sh: launcher not found: %s\n' "$exe" >&2
        exit 2
    fi
    printf '%s/%s' "$(cd "$(dirname "$exe")" && pwd)" "$(basename "$exe")"
}

configs=()
cleanup() { rm -f "${configs[@]:-}"; }
trap cleanup EXIT

names=()
for pair in "left:$left_exe:$left_name" "right:$right_exe:$right_name"; do
    side="${pair%%:*}"; rest="${pair#*:}"
    exe="${rest%%:*}"; nm="${rest#*:}"
    cfg="$(mktemp -t "solvik-tck-diff-$side-XXXXXX.json")"
    configs+=("$cfg")
    python3 "$root/tck/configs/make-adapter-config.py" "$(abs "$exe")" "$nm" "$cfg" >/dev/null
    names+=("$nm")
done

printf 'tck-differential.sh: comparing %s vs %s over the portable corpus...\n' \
    "${names[0]}" "${names[1]}"

# Capture (do not let `set -e` abort) the exit code, so the outcome is described in the
# project's own terms rather than leaking a tool status:
#   0 = no disagreement, 1 = at least one disagreement, 2 = nothing could be compared.
rc=0
python3 "$root/tck/runner/tck_cli.py" differential \
    --left-config "${configs[0]}" --right-config "${configs[1]}" --report "$report" || rc=$?

case "$rc" in
    0)
        printf 'tck-differential.sh: OK (%s vs %s agree on every compared test)\n' \
            "${names[0]}" "${names[1]}"
        printf 'tck-differential.sh: NOTE agreement is differential evidence, not conformance;\n'
        printf 'tck-differential.sh: NOTE conformance is decided only by tck-run.sh against the oracle.\n'
        exit 0
        ;;
    1)
        printf 'tck-differential.sh: DISAGREEMENT between %s and %s (see %s)\n' \
            "${names[0]}" "${names[1]}" "$report" >&2
        printf 'tck-differential.sh: two builds of one specification must not diverge; this is a defect.\n' >&2
        exit 1
        ;;
    *)
        printf 'tck-differential.sh: differential run could not compare any test; treating as an infrastructure error.\n' >&2
        exit 2
        ;;
esac
