#!/usr/bin/env bash
# Benchmark the Solvik-to-Java transpiler and the Java it generates.
#
# This suite is intentionally manual: it is not part of ./build.sh because
# wall-clock numbers depend on the machine and the JVM. It measures:
#
#   * transpiler wall-clock time (time to emit one .java file)
#   * javac time for the generated source
#   * generated-program execution time (fresh JVM, so it includes startup)
#   * the same execution for the hand-written Java equivalent when present
#   * optional per-phase compiler timings
#   * optional large-source compiler scaling
#
# Usage:
#   benchmarks/run.sh                 # run benchmarks/solvik/*.sol
#   benchmarks/run.sh --phases        # per-phase compiler timing
#   benchmarks/run.sh --large         # compiler scaling on generated sources
#   RUNS=9 WARMUP=3 benchmarks/run.sh
#
# Environment:
#   RUNS    measured iterations per benchmark (default 7)
#   WARMUP  warm-up iterations per benchmark (default 2)
set -euo pipefail
cd "$(dirname "$0")"

repo="$PWD/.."
bench_dir="$PWD"
transpiler="$repo/target/solvik.jar"
if [ ! -f "$transpiler" ]; then
    echo "transpiler jar not found: $transpiler (run ./build.sh first)" >&2
    exit 1
fi

runs="${RUNS:-7}"
warmup="${WARMUP:-2}"
mode="${1:-run}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/classes"

now_ns() { date +%s%N; }

median() {
    printf '%s\n' "$@" | sort -n | awk '{ values[NR] = $1 } END { print values[int((NR + 1) / 2)] }'
}

# Transpile $1 to $2 (class name) inside $work. Prints elapsed milliseconds.
transpile_once() {
    local source="$1" class="$2" path
    local start end
    case "$source" in
        /*) path="$source" ;;
        *) path="$PWD/$source" ;;
    esac
    start="$(now_ns)"
    (cd "$work" && java -jar "$transpiler" "$path" "$class") >/dev/null 2>&1
    end="$(now_ns)"
    echo $(( (end - start) / 1000000 ))
}

# Median transpile time for a source/class pair.
transpile_median() {
    local source="$1" class="$2" i times=()
    for ((i = 0; i < warmup; i++)); do transpile_once "$source" "$class" >/dev/null; done
    for ((i = 0; i < runs; i++)); do times+=("$(transpile_once "$source" "$class")"); done
    median "${times[@]}"
}

# Time javac of a generated class $1 in $work. Prints elapsed milliseconds.
javac_once() {
    local class="$1" start end
    start="$(now_ns)"
    javac --release 17 -Xlint:all -Werror -d "$work/classes" "$work/$class.java" >/dev/null 2>&1
    end="$(now_ns)"
    echo $(( (end - start) / 1000000 ))
}

javac_median() {
    local class="$1" i times=()
    for ((i = 0; i < warmup; i++)); do javac_once "$class" >/dev/null; done
    for ((i = 0; i < runs; i++)); do times+=("$(javac_once "$class")"); done
    median "${times[@]}"
}

# Time `java class` (fresh JVM). Prints `milliseconds output`.
run_once() {
    local class="$1" start end out
    start="$(now_ns)"
    out="$(java -cp "$work/classes" "$class" 2>&1)"
    end="$(now_ns)"
    echo "$(( (end - start) / 1000000 )) $out"
}

run_median() {
    local class="$1" i times=() out=""
    for ((i = 0; i < warmup; i++)); do run_once "$class" >/dev/null; done
    for ((i = 0; i < runs; i++)); do
        read -r ms out < <(run_once "$class")
        times+=("$ms")
    done
    echo "$(median "${times[@]}") $out"
}

run_benchmarks() {
    javac --release 17 -d "$work/classes" java/EmptyJava.java >/dev/null 2>&1
    read -r startup_ms _ < <(run_median "EmptyJava")
    echo "jvm startup baseline: ${startup_ms} ms"
    printf '%-12s %10s %10s %14s %14s %10s\n' \
        benchmark transpile javac solvik-run java-run ratio
    printf '%-12s %10s %10s %14s %14s %10s\n' \
        ---------- --------- --------- -------------- -------------- ---------

    for source in solvik/*.sol; do
        name="$(basename "$source" .sol)"
        class="Bench_${name}"
        # Generated programs live in a package-free class; the source name is
        # only used for diagnostics.
        t_median="$(transpile_median "solvik/$name.sol" "$class")"
        javac_ms="$(javac_median "$class")"
        read -r solvik_ms solvik_out < <(run_median "$class")

        java_ms="-"
        java_out=""
        java_file="java/$(echo "${name:0:1}" | tr '[:lower:]' '[:upper:]')${name:1}Java.java"
        if [ -f "$java_file" ]; then
            java_class="$(basename "$java_file" .java)"
            javac --release 17 -Xlint:all -Werror -d "$work/classes" "$java_file" >/dev/null 2>&1
            read -r java_ms java_out < <(run_median "$java_class")
            if [ "$java_out" != "$solvik_out" ]; then
                echo "WARNING: $name output differs: solvik='$solvik_out' java='$java_out'" >&2
            fi
        fi
        ratio="-"
        if [ "$java_ms" != "-" ] && [ "$java_ms" -gt 0 ]; then
            ratio="$(awk -v a="$solvik_ms" -v b="$java_ms" 'BEGIN { printf "%.2fx", a / b }')"
        fi
        printf '%-12s %8s ms %8s ms %11s ms %11s ms %10s\n' \
            "$name" "$t_median" "$javac_ms" "$solvik_ms" "$java_ms" "$ratio"
    done
}

run_phases() {
    local classes="$repo/target/classes"
    if [ ! -d "$classes" ]; then
        echo "transpiler classes not found: $classes (run ./build.sh first)" >&2
        exit 1
    fi
    mkdir -p "$work/phase-classes"
    javac -cp "$classes" -d "$work/phase-classes" PhaseBench.java
    java -cp "$work/phase-classes:$classes" PhaseBench "$bench_dir"/solvik/*.sol
}

run_large() {
    local classes="$repo/target/classes"
    if [ ! -d "$classes" ]; then
        echo "transpiler classes not found: $classes (run ./build.sh first)" >&2
        exit 1
    fi
    local files=()
    for kind in structs traits locals; do
        for count in 500 1000 2000 4000; do
            source="$work/large_${kind}_${count}.sol"
            ./generate_large.sh "$count" "$source" "$kind" >/dev/null
            files+=("$source")
        done
    done
    mkdir -p "$work/phase-classes"
    javac -cp "$classes" -d "$work/phase-classes" PhaseBench.java
    echo "compiler scaling on generated sources (warmed, single-JVM medians)"
    java -cp "$work/phase-classes:$classes" PhaseBench "${files[@]}"
}

case "$mode" in
    --phases) run_phases ;;
    --large) run_large ;;
    -h|--help)
        sed -n '2,25p' "$0"
        ;;
    *) run_benchmarks ;;
esac
