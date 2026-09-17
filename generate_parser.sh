#!/usr/bin/env bash
#
# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
# Copyright (c) 2026-present Douglas Hoard
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

curl -O https://www.antlr.org/download/antlr-4.13.2-complete.jar
# The inherited SimpleLanguage grammar and parser were removed in Phase 5; only the Solvik
# grammar below is generated.

# --- Solvik grammar (Phase 1+) ------------------------------------------------------------
#
# Generates the Solvik parser from language/src/main/java/org/solvik/parser/grammar/Solvik.g4
# into language/src/main/java/org/solvik/parser/generated/, then applies the conventions that
# this repository keeps in its checked-in generated sources:
#
#   1. replace the single-line ANTLR banner with the Apache-2.0 license header wrapped in
#      formatter/checkstyle pragmas;
#   2. narrow the top-level class @SuppressWarnings list (the tool emits deprecated lint tags);
#   3. ensure a trailing newline (the tool omits one for visitor interfaces).
#
# Note: ANTLR 4.13 renders parser rule labels (`name=rule`) as `((XxxContext)_localctx).name =`
# casts. The Solvik grammar avoids rule labels so the generated output stays uniform; add no
# postprocessing assumption beyond the three steps above.
#
# Lexer conventions owned by the grammar (Phase 2): physical newlines are hidden NEWLINE tokens and
# comment bodies are hidden comment tokens so that org.solvik.parser.SemicolonInsertingTokenSource
# can implement the lexical semicolon insertion of docs/LANGUAGE_SPEC.md section 16. A combined
# grammar cannot declare custom channels, so all of them use the built-in HIDDEN channel and are
# distinguished by token type in that stage.
#
# Lexer conventions owned by the grammar (Phase 3): RAW_STRING_LITERAL matches the contiguous
# 'r' '#'* '"' opening delimiter and its action scans the counted body in @lexer::members, so the
# complete raw string is one token and its physical newlines never reach semicolon insertion. The
# generated action dispatch and members are part of this grammar's output and must not be edited in
# the generated files.
#
# These steps are idempotent; rerunning this script produces byte-identical output.
SOLVIK_PARSER_DIR=language/src/main/java/org/solvik/parser/generated
SOLVIK_GRAMMAR=language/src/main/java/org/solvik/parser/grammar/Solvik.g4

$JAVA_HOME/bin/java -cp antlr-4.13.2-complete.jar org.antlr.v4.Tool \
    -package org.solvik.parser.generated -no-listener -visitor -Xexact-output-dir \
    -o "$SOLVIK_PARSER_DIR" "$SOLVIK_GRAMMAR"

for f in "$SOLVIK_PARSER_DIR"/SolvikLexer.java \
         "$SOLVIK_PARSER_DIR"/SolvikParser.java \
         "$SOLVIK_PARSER_DIR"/SolvikVisitor.java \
         "$SOLVIK_PARSER_DIR"/SolvikBaseVisitor.java; do
    tmp="$(mktemp)"
    {
        printf '%s\n' \
'/*' \
' * Copyright (c) 2026-present Douglas Hoard' \
' *' \
' * Licensed under the Apache License, Version 2.0 (the "License");' \
' * you may not use this file except in compliance with the License.' \
' * You may obtain a copy of the License at' \
' *' \
' * http://www.apache.org/licenses/LICENSE-2.0' \
' *' \
' * Unless required by applicable law or agreed to in writing, software' \
' * distributed under the License is distributed on an "AS IS" BASIS,' \
' * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.' \
' * See the License for the specific language governing permissions and' \
' * limitations under the License.' \
' */' \
'// Checkstyle: stop' \
'//@formatter:off'
        tail -n +2 "$f"
    } > "$tmp"
    sed -i \
        -e 's/@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})/@SuppressWarnings({"all", "this-escape"})/' \
        "$tmp"
    if [ -n "$(tail -c 1 "$tmp")" ]; then
        echo >> "$tmp"
    fi
    cat "$tmp" > "$f"
    rm -f "$tmp"
done
