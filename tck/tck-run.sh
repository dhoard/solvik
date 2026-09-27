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

# Solvik TCK conformance run against a built distribution (TCK.md sections 9, 13).
#
# This is the *runtime* half of the TCK gate (tck-check.sh is the early, pure-Python
# half that runs before compilation). It drives the portable runner against a real
# launcher -- the JVM distribution or the native image -- exactly as an independent
# conformance consumer would: it generates an adapter configuration whose fingerprint
# is the value the adapter itself computes for that executable, then runs every
# corpus test through the subprocess protocol and records a versioned report.
#
# A conformance failure or an infrastructure error is a hard, nonzero build result.
# Full-profile *certification* is reported separately in the report and is withheld
# while any active requirement is untested (the requirement-coverage gap), so the
# build gate asserts that every executed test passes and nothing errored.
#
# Usage (from anywhere): ./tck/tck-run.sh <launcher-executable> <adapter-name>

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"

launcher="${1:-}"
name="${2:-}"
if [[ -z "$launcher" || -z "$name" ]]; then
    printf 'usage: %s <launcher-executable> <adapter-name>\n' "$0" >&2
    exit 2
fi
if [[ ! -x "$launcher" && ! -f "$launcher" ]]; then
    printf 'tck-run.sh: launcher not found: %s\n' "$launcher" >&2
    exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
    printf 'tck-run.sh: python3 is required for the portable TCK runner.\n' >&2
    exit 2
fi

# Make the launcher path absolute (the adapter records it and the launcher resolves
# its own distribution directory from it), then generate the matching config.
abs_launcher="$(cd "$(dirname "$launcher")" && pwd)/$(basename "$launcher")"
config="$(mktemp -t solvik-tck-config-XXXXXX.json)"
report_dir="$root/tck/reports"
report="$report_dir/conformance-$name.json"
trap 'rm -f "$config"' EXIT

mkdir -p "$report_dir"
printf 'tck-run.sh: generating adapter config for %s (%s)...\n' "$name" "$abs_launcher"
python3 "$root/tck/configs/make-adapter-config.py" "$abs_launcher" "$name" "$config"

printf 'tck-run.sh: running conformance suite against %s...\n' "$name"
# Capture (do not let `set -e` abort) the runner exit code: 0 = all executed tests
# passed, 1 = one or more conformance failures, 2 = infrastructure/validation error.
rc=0
python3 "$root/tck/runner/tck_cli.py" run --adapter-config "$config" --report "$report" || rc=$?
if [[ "$rc" -eq 0 ]]; then
    printf 'tck-run.sh: OK (%s)\n' "$name"
    exit 0
fi
printf 'tck-run.sh: conformance run failed for %s (exit %d); report: %s\n' "$name" "$rc" "$report" >&2
exit "$rc"
