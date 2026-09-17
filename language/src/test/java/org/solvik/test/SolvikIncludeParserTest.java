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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
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
        assertFalse("parse must fail: " + text, result.isSuccess());
        assertTrue(result.diagnostics().hasErrors());
        return result.diagnostics();
    }

    private static IncludeDeclNode onlyInclude(CompilationUnitNode unit) {
        assertEquals(1, unit.items().size());
        assertTrue(unit.items().get(0) instanceof IncludeDeclNode);
        return (IncludeDeclNode) unit.items().get(0);
    }

    @Test
    public void normalStringIncludeWithInsertedSemicolon() {
        CompilationUnitNode unit = parseOk("include \"lib/math.sol\"\n");
        IncludeDeclNode include = onlyInclude(unit);
        assertEquals(AstKind.INCLUDE_DECL, include.kind());
        assertTrue(include.pathLiteral() instanceof StringLiteralNode);
        assertEquals("\"lib/math.sol\"", ((StringLiteralNode) include.pathLiteral()).lexeme());
        assertEquals("include \"lib/math.sol\"", "include \"lib/math.sol\"\n".substring(include.span().startOffset(), include.span().endOffset()));
        assertTrue(unit.hasUnresolvedIncludes());
    }

    @Test
    public void normalStringIncludeWithExplicitSemicolon() {
        CompilationUnitNode unit = parseOk("include \"lib/math.sol\";\n");
        IncludeDeclNode include = onlyInclude(unit);
        assertEquals("\"lib/math.sol\"", ((StringLiteralNode) include.pathLiteral()).lexeme());
    }

    @Test
    public void rawStringIncludeCarriesValue() {
        CompilationUnitNode unit = parseOk("include r#\"lib/generated.sol\"#\n");
        IncludeDeclNode include = onlyInclude(unit);
        assertTrue(include.pathLiteral() instanceof RawStringLiteralNode);
        RawStringLiteralNode raw = (RawStringLiteralNode) include.pathLiteral();
        assertEquals("lib/generated.sol", raw.value());
        assertEquals(1, raw.hashCount());
    }

    @Test
    public void includesAndDeclarationsKeepSourceOrder() {
        String text = "include \"a.sol\"\n" //
                        + "func helper(): Int {\n    return 1\n}\n" //
                        + "include \"b.sol\"\n" //
                        + "println(\"done\")\n";
        CompilationUnitNode unit = parseOk(text);
        List<AstKind> kinds = unit.items().stream().map(AstNode::kind).toList();
        assertEquals(List.of(AstKind.INCLUDE_DECL, AstKind.FUNCTION_DECL, AstKind.INCLUDE_DECL, AstKind.EXPR_STMT), kinds);
        // The unit exposes declarations and statements with the includes excluded.
        assertEquals(1, unit.declarations().size());
        assertEquals(1, unit.statements().size());
    }

    @Test
    public void noIncludesLeavesUnitResolved() {
        CompilationUnitNode unit = parseOk("println(\"hi\")\n");
        assertFalse(unit.hasUnresolvedIncludes());
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
