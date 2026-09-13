#!/usr/bin/env bash
# Build and test the Solvik-to-Java transpiler through the Maven Wrapper.
set -euo pipefail
cd "$(dirname "$0")"
exec ./mvnw -B clean verify
