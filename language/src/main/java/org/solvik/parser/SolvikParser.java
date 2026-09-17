/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.parser;

import java.util.Objects;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.parser.generated.SolvikLexer;
import org.solvik.parser.generated.SolvikParser.CompilationUnitContext;
import org.solvik.source.SourceFile;

/**
 * Entry point of the Solvik front-end parser: {@link SourceFile} in, {@link SolvikParseResult} out.
 *
 * <p>This adapter depends only on Truffle-free types: the syntax AST, the diagnostic framework, and
 * the ANTLR runtime. It has no dependency on executable Truffle nodes, and nothing here lowers or
 * executes Solvik code. The inherited SimpleLanguage parser is never referenced.
 *
 * <p>Error policy: the generated parser uses ANTLR's default error strategy, which records one or
 * more syntax problems and then attempts local recovery solely to continue reporting. The rebuilt
 * tree is consumed only when zero error diagnostics were produced; on any error the result carries
 * diagnostics and no AST, so recovery can never smuggle a partial program into later phases.
 *
 * <p>Statement termination follows docs/LANGUAGE_SPEC.md section 16: the raw lexer stream passes
 * through {@link SemicolonInsertingTokenSource}, which injects synthetic {@code SEMI} tokens at
 * physical line boundaries purely lexically. Parser errors therefore cannot influence where
 * statements terminate.
 */
public final class SolvikParser {

    private SolvikParser() {
    }

    /** Parses an entire Solvik source file. Malformed input produces diagnostics, not exceptions. */
    public static SolvikParseResult parse(SourceFile source) {
        Objects.requireNonNull(source, "source");
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(source.text(), source.name()));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(new SemicolonInsertingTokenSource(lexer));
        org.solvik.parser.generated.SolvikParser parser = new org.solvik.parser.generated.SolvikParser(tokens);
        parser.removeErrorListeners();
        SolvikErrorListener listener = new SolvikErrorListener(source);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        CompilationUnitContext tree = parser.compilationUnit();
        DiagnosticBag bag = listener.build();
        if (bag.hasErrors()) {
            return SolvikParseResult.failure(bag);
        }
        CompilationUnitNode ast = new SolvikAstBuilder(source).build(tree);
        return SolvikParseResult.success(ast);
    }
}
