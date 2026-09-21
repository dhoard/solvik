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
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.IncludeDeclNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;

/** Parser coverage for the compile-time {@code include} directive (docs/LANGUAGE_SPEC.md section 20). */
public final class SolvikIncludeParserTest {

    private static CompilationUnitNode parseOk(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("root.sol", text));
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("unexpected parse errors:");
            result.diagnostics().all().forEach(d -> sb.append("\n  ").append(d));
            throw new AssertionError(sb.toString());
        }
        return result.requireAst();
    }

    private static DiagnosticBag parseFails(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("root.sol", text));
        assertThat(result.isSuccess()).as("parse must fail: " + text).isFalse();
        assertThat(result.diagnostics().hasErrors()).isTrue();
        return result.diagnostics();
    }

    private static IncludeDeclNode onlyInclude(CompilationUnitNode unit) {
        assertThat(unit.items().size()).isEqualTo(1);
        assertThat(unit.items().get(0) instanceof IncludeDeclNode).isTrue();
        return (IncludeDeclNode) unit.items().get(0);
    }

    @Test
    public void normalStringIncludeWithInsertedSemicolon() {
        CompilationUnitNode unit = parseOk("include \"lib/math.sol\"\n");
        IncludeDeclNode include = onlyInclude(unit);
        assertThat(include.kind()).isEqualTo(AstKind.INCLUDE_DECL);
        assertThat(include.pathLiteral() instanceof StringLiteralNode).isTrue();
        assertThat(((StringLiteralNode) include.pathLiteral()).lexeme()).isEqualTo("\"lib/math.sol\"");
        assertThat("include \"lib/math.sol\"\n".substring(include.span().startOffset(), include.span().endOffset())).isEqualTo("include \"lib/math.sol\"");
        assertThat(unit.hasUnresolvedIncludes()).isTrue();
    }

    @Test
    public void normalStringIncludeWithExplicitSemicolon() {
        CompilationUnitNode unit = parseOk("include \"lib/math.sol\";\n");
        IncludeDeclNode include = onlyInclude(unit);
        assertThat(((StringLiteralNode) include.pathLiteral()).lexeme()).isEqualTo("\"lib/math.sol\"");
    }

    @Test
    public void rawStringIncludeCarriesValue() {
        CompilationUnitNode unit = parseOk("include r#\"lib/generated.sol\"#\n");
        IncludeDeclNode include = onlyInclude(unit);
        assertThat(include.pathLiteral() instanceof RawStringLiteralNode).isTrue();
        RawStringLiteralNode raw = (RawStringLiteralNode) include.pathLiteral();
        assertThat(raw.value()).isEqualTo("lib/generated.sol");
        assertThat(raw.hashCount()).isEqualTo(1);
    }

    @Test
    public void includesAndDeclarationsKeepSourceOrder() {
        String text = "include \"a.sol\"\n" //
                        + "func helper(): Integer {\n    return 1\n}\n" //
                        + "include \"b.sol\"\n" //
                        + "println(\"done\")\n";
        CompilationUnitNode unit = parseOk(text);
        List<AstKind> kinds = unit.items().stream().map(AstNode::kind).toList();
        assertThat(kinds).isEqualTo(List.of(AstKind.INCLUDE_DECL, AstKind.FUNCTION_DECL, AstKind.INCLUDE_DECL, AstKind.EXPR_STMT));
        // The unit exposes declarations and statements with the includes excluded.
        assertThat(unit.declarations().size()).isEqualTo(1);
        assertThat(unit.statements().size()).isEqualTo(1);
    }

    @Test
    public void noIncludesLeavesUnitResolved() {
        CompilationUnitNode unit = parseOk("println(\"hi\")\n");
        assertThat(unit.hasUnresolvedIncludes()).isFalse();
    }

    @Test
    public void includeCanNoLongerBeAnIdentifier() {
        parseFails("val include = 1\n");
    }

    @Test
    public void missingPathFails() {
        parseFails("include\n");
    }

    @Test
    public void sameLineFollowingItemWithoutSemicolonFails() {
        parseFails("include \"a.sol\" println(\"x\")\n");
    }

    @Test
    public void includeInsideBlockFails() {
        parseFails("func f(): Unit {\n    include \"a.sol\"\n}\n");
    }

    @Test
    public void aliasFormsFail() {
        parseFails("include \"a.sol\" as b\n");
        parseFails("include \"a.sol\" as \"b.sol\"\n");
    }
}
