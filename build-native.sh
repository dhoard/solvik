#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME=/opt/graalvm
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"
exec ./mvnw clean package -Pnative "$@"
