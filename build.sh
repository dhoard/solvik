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

# Solvik requires GraalVM for JDK 25 (with native-image). Locate it from an explicit
# GRAALVM_HOME, then a GraalVM JAVA_HOME, then the conventional /opt/graalvm location.
# A non-GraalVM JAVA_HOME is ignored so the host JDK is never used by accident.
is_graalvm25() {
    local home="$1"
    [[ -x "$home/bin/java" ]] || return 1
    [[ -f "$home/release" ]] || return 1
    grep -q '^GRAALVM_VERSION=' "$home/release" 2>/dev/null || return 1
    [[ "$(sed -n 's/^JAVA_VERSION="\([0-9][0-9]*\).*/\1/p' "$home/release")" == 25 ]]
}

graalvm_home=""
for candidate in "${GRAALVM_HOME:-}" "${JAVA_HOME:-}" /opt/graalvm; do
    [[ -n "$candidate" ]] || continue
    if is_graalvm25 "$candidate"; then
        graalvm_home="$candidate"
        break
    fi
done

if [[ -z "$graalvm_home" ]]; then
    printf 'build.sh: GraalVM for JDK 25 is required.\n' >&2
    printf 'Install GraalVM for JDK 25 and set GRAALVM_HOME or JAVA_HOME to it,\n' >&2
    printf 'or install it at /opt/graalvm.\n' >&2
    exit 1
fi

export JAVA_HOME="$graalvm_home"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"
# Runs under `set -e`: a failing Maven build aborts before the corpus step.
./mvnw clean package "$@"

# End-user verification: run the checked-in .sol corpus through the JVM launcher
# exactly the way a user invokes it, comparing against the golden .output files.
# The in-process Context.eval suites (SolvikProgramTest / SolvikRegressionProgramTest)
# do not exercise this shipped entry point. build-native.sh sets
# SOLVIK_SKIP_CORPUS=1 because that invocation verifies the native binary instead
# (the JVM launcher is covered by ./build.sh and by ./build-all.sh).
if [[ "${SOLVIK_SKIP_CORPUS:-0}" != "1" ]]; then
    ./test-corpus.sh ./standalone/target/solvik --engine.WarnInterpreterOnly=false
fi
