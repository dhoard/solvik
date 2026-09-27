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

# build.sh selects GraalVM for JDK 25 and runs the Maven wrapper; this wrapper adds the
# native-image distribution profile.
# SOLVIK_SKIP_CORPUS=1 suppresses build.sh's JVM-launcher corpus pass, which would
# otherwise run the whole corpus twice inside this invocation; the JVM launcher is
# covered by ./build.sh (and by ./build-all.sh, which runs it first). Here the corpus
# is run against the freshly produced native-image binary instead, so the AOT
# distribution is verified the way an end user runs it rather than merely built.
# SOLVIK_SKIP_TCK=1 suppresses build.sh's early TCK self-test + validation pass,
# which does not depend on the freshly built native binary and is already covered
# by ./build.sh (and first by ./build-all.sh). Skipping avoids re-running pure-
# Python self-tests on every native build; the TCK gate is still enforced by
# ./build.sh and by ./build-all.sh.
cd "$(dirname "$0")"
SOLVIK_SKIP_CORPUS=1 SOLVIK_SKIP_TCK=1 ./build.sh -Pnative "$@"
./test-corpus.sh ./standalone/target/solvik-native
# Conformance run against the native-image distribution (a distinct IUT identity:
# different fingerprint from the JVM run). build.sh set SOLVIK_SKIP_CORPUS=1, which
# also skips the JVM conformance run here because it is covered by ./build.sh and by
# ./build-all.sh; the native conformance run is the purpose of this wrapper.
if [[ "${SOLVIK_SKIP_CORPUS:-0}" != "1" ]]; then
    ./tck/tck-run.sh ./standalone/target/solvik-native solvik-native
    # Differential run (TCK.md section 15): the same portable programs through both
    # shipped distributions. Both launchers exist at this point, and a disagreement
    # between them is a defect in this repository regardless of any oracle, so it is a
    # hard failure here. It is not a conformance verdict and cannot update or override
    # an oracle; tck-run.sh remains the only source of PASS/FAIL.
    ./tck/tck-differential.sh ./standalone/target/solvik solvik-jvm \
        ./standalone/target/solvik-native solvik-native
fi
