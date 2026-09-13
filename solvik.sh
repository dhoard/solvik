#!/usr/bin/env bash
# Transpile and run a Solvik program:
#
#   ./solvik.sh <input.sol> [program args...]
#
# The program is transpiled into a temporary directory, compiled with a Java 17
# javac, and executed. Remaining arguments are forwarded to the Solvik program,
# and its exit code and streams are preserved.
set -euo pipefail
repo="$(cd "$(dirname "$0")" && pwd)"
jar="$repo/target/solvik.jar"
if [ ! -f "$jar" ]; then
    echo "error: $jar not found; run ./build.sh first" >&2
    exit 3
fi
if [ "$#" -lt 1 ]; then
    echo "usage: $0 <input.sol> [program args...]" >&2
    exit 3
fi
input="$1"
shift
if [ ! -f "$input" ] || [[ "$input" != *.sol ]]; then
    echo "error: input must be an existing .sol file: $input" >&2
    exit 3
fi
input="$(cd "$(dirname "$input")" && pwd)/$(basename "$input")"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

class="SolvikProgram"
(cd "$work" && java -jar "$jar" "$input" "$class")
javac --release 17 -Xlint:all -Werror -d "$work/classes" "$work/$class.java"
java -cp "$work/classes" "$class" "$@"
