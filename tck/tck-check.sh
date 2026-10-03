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

# Early, independent TCK gate (TCK.md section 16): validate all versioned inputs
# and run the portable runner self-tests. These are pure-Python and invoke no
# Solvik, Java, GraalVM, Maven, or built distribution. A failure here is a hard,
# nonzero build result and runs before any (expensive) compilation.
#
# Usage (from anywhere): ./tck/tck-check.sh

here="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$here/.." && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
    printf 'tck-check.sh: python3 is required for the portable TCK runner.\n' >&2
    exit 2
fi

# Fail if python3 is older than 3.9 (the runner uses no newer features, but the
# standard-library API surface relied on is 3.9+).
python3 - <<'PY' || { printf 'tck-check.sh: python3 >= 3.9 is required.\n' >&2; exit 2; }
import sys
raise SystemExit(0 if sys.version_info >= (3, 9) else 1)
PY

printf 'tck-check.sh: validating versioned inputs (schemas, inventory, profiles, corpus)...\n'
python3 "$here/runner/tck_cli.py" validate

printf 'tck-check.sh: running portable runner self-tests (no Solvik/Java/GraalVM)...\n'
python3 "$here/tests/run_selftests.py"

# Provenance: regenerate the corpus surface that tck/tools/gen*.py own into a
# throwaway repository built from an empty corpus, and require byte equality with
# the committed artifacts. Pure Python, and it writes only into a temp directory.
printf 'tck-check.sh: verifying generated-artifact provenance (no Solvik/Java/GraalVM)...\n'
python3 "$here/tools/verify_regen.py"

# Keyword-vocabulary guards. The 2026.11-draft revision removed `var`, `open`, and
# `sealed`; these two scans keep the *prose* layers current, which the corpus gate
# (SolvikRemovedKeywordCorpusTest, a JUnit test over .sol and .output files) cannot
# see because prose carries no tokens. Both read committed files only.
printf 'tck-check.sh: checking requirement prose against the current keyword vocabulary...\n'
python3 "$ROOT/tools/repair-keyword-summaries.py" --check

printf 'tck-check.sh: checking generator prose against the current keyword vocabulary...\n'
python3 "$ROOT/tools/check-generator-keyword-prose.py"

printf 'tck-check.sh: OK\n'
