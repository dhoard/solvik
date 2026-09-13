#!/usr/bin/env bash
# Run the built Solvik transpiler: ./transpile.sh <input.sol> <OutputClassName>
#
# The generated Java file is written into the caller's current working
# directory, regardless of where this script or the JAR live.
set -euo pipefail
repo="$(cd "$(dirname "$0")" && pwd)"
jar="$repo/target/solvik.jar"
if [ ! -f "$jar" ]; then
    echo "error: $jar not found; run ./build.sh first" >&2
    exit 3
fi
exec java -jar "$jar" "$@"
