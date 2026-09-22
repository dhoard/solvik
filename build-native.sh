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
cd "$(dirname "$0")"
SOLVIK_SKIP_CORPUS=1 ./build.sh -Pnative "$@"
./test-corpus.sh ./standalone/target/solvik-native
