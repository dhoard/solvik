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

# Runs every collection benchmark (or the named subset) and prints one row per benchmark with the
# best wall time over ROUNDS rounds. Each program prints a checksum that run_benchmarks_in_test
# compares against its .output golden, so a benchmark that changes behavior fails loudly here too.

cd "$(dirname "$0")"
directory="$(pwd)"
modules="${SOLVIK_MODULES:-${directory}/../standalone/target/modules}"
rounds="${ROUNDS:-3}"

if [[ ! -d "$modules" ]]; then
    printf 'run.sh: module directory not found: %s\n' "$modules" >&2
    printf 'run.sh: build the language and assemble standalone/target/modules first.\n' >&2
    exit 1
fi

# Same GraalVM-for-JDK-25 discovery rule build.sh uses, so the host JDK never measures a run.
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
    printf 'run.sh: GraalVM for JDK 25 is required (set GRAALVM_HOME or JAVA_HOME, or install /opt/graalvm).\n' >&2
    exit 1
fi

java="$graalvm_home/bin/java"

# Fixed order, so a comparison of two run.sh outputs lines up row by row.
all=(
    loop-floor
    list-add
    list-add-erased
    list-get
    list-get-erased
    list-size
    stack-push-pop
    set-build
    set-build-4x
    set-contains
    map-build
    map-build-4x
    map-lookup
)

if [[ $# -gt 0 ]]; then
    selected=("$@")
else
    selected=("${all[@]}")
fi

# Warm the JIT and the module loading once per program before timing, so a cold first process does
# not dominate a short benchmark.
warmup() {
    local program="$1"
    "$java" -p "$modules" -m org.solvik.launcher/org.solvik.launcher.SolvikMain "${directory}/${program}.sol" >/dev/null 2>&1
}

best_time() {
    local program="$1"
    local best=""
    local elapsed
    for _ in $(seq 1 "$rounds"); do
        elapsed=$(/usr/bin/time -f '%e' "$java" -p "$modules" -m org.solvik.launcher/org.solvik.launcher.SolvikMain "${directory}/${program}.sol" 2>&1 >/dev/null | tail -1)
        # /usr/bin/time writes the elapsed time to stderr; ignore any warning lines it shares with.
        elapsed="${elapsed##* }"
        if [[ -z "$best" ]] || awk -v a="$elapsed" -v b="$best" 'BEGIN { exit !(a < b) }'; then
            best="$elapsed"
        fi
    done
    printf '%s' "$best"
}

check_output() {
    local program="$1"
    local golden="${directory}/${program}.output"
    [[ -f "$golden" ]] || return 0
    local actual
    actual=$("$java" -p "$modules" -m org.solvik.launcher/org.solvik.launcher.SolvikMain "${directory}/${program}.sol" 2>/dev/null)
    if [[ "$actual" != "$(cat "$golden")" ]]; then
        printf 'run.sh: %s output does not match %s\n' "$program" "$golden" >&2
        return 1
    fi
}

printf '%-18s %8s\n' benchmark seconds
for program in "${selected[@]}"; do
    if [[ ! -f "${directory}/${program}.sol" ]]; then
        printf 'run.sh: no benchmark named %s\n' "$program" >&2
        exit 1
    fi
    check_output "$program"
    warmup "$program"
    printf '%-18s %8s\n' "$program" "$(best_time "$program")"
done
