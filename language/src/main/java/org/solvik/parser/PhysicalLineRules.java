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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;
import org.solvik.parser.generated.SolvikLexer;

/**
 * The physical-line rules of {@code docs/LANGUAGE_SPEC.md} section 16 that the phrase grammar cannot
 * state: an opening brace is the last token on the line of the construct that introduces its scope, a
 * closing brace is the only significant token on its line, a clause keyword begins its line, and the
 * {@code for} header the 2026.10 revision separated with {@code ;} is gone.
 *
 * <p>These are facts about physical lines, not about phrase structure: {@code Solvik.g4} absorbs the
 * line breaks a program writes so that a body can be read at all, which means a wrongly placed brace
 * still parses, and a rule written in the grammar would have to duplicate every body production once
 * per line shape. Checking the token sequence instead states each rule once, locates it exactly, and
 * leaves the grammar free to describe what a program means.
 *
 * <p>The stage runs after a successful parse and reads only the default-channel token sequence - the
 * same sequence the parser saw, with comments and blank lines removed - so it never contradicts the
 * parser and never reports on text inside a comment or a literal. Because the line-boundary stream
 * already decided which breaks end a line, a token pair on one physical line here means the programmer
 * wrote them on one physical line.
 *
 * <p>Two omissions are deliberate and stated rather than hidden. A stand-alone scope block writes its
 * {@code &#123;} as the first token of its line, which is legal, so a brace that opens a line is
 * reported only when the token before it could have introduced a body - a header's {@code )},
 * {@code try}, {@code else}, or {@code static}. And a `;` left at the end of a
 * line is redundant rather than wrong: {@code ;} separates statements of one line and the grammar
 * tolerates closing a line with one.
 */
public final class PhysicalLineRules {

    private PhysicalLineRules() {
    }

    /**
     * Every physical-line violation in a program that parsed.
     *
     * @param source the file the tokens came from, for span identity
     * @param tokens the stream the parser parsed, on all channels
     * @return the diagnostics to report, in source order
     */
    public static List<Diagnostic> check(SourceFile source, BufferedTokenStream tokens) {
        tokens.fill();
        List<Token> code = significant(tokens);
        List<Diagnostic> found = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            Token token = code.get(i);
            if (token.getType() == SolvikLexer.LBRACE) {
                openingBrace(source, code, i, found);
                misplacedBodyBrace(source, code, i, found);
            } else if (token.getType() == SolvikLexer.RBRACE) {
                closingBrace(source, code, i, found);
            }
        }
        return found;
    }

    /**
     * The removed syntax this stage can still name from the token stream alone, before any parse has
     * run. A program containing it is rejected with these diagnostics rather than with the generic
     * syntax errors its removed spelling would otherwise produce, because the removed spelling is the
     * thing the programmer should be told about.
     */
    public static List<Diagnostic> removedSyntax(SourceFile source, BufferedTokenStream tokens) {
        tokens.fill();
        List<Token> code = significant(tokens);
        List<Diagnostic> found = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i).getType() == SolvikLexer.FOR) {
                threeClauseForHeader(source, code, i, found);
            }
        }
        return found;
    }

    /** A `{` may be followed on its line by nothing, or by the `}` of an empty body. */
    private static void openingBrace(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token next = next(code, index);
        if (next == null || next.getLine() != code.get(index).getLine() || next.getType() == SolvikLexer.RBRACE) {
            return;
        }
        found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_CONTENT_AFTER_OPEN_BRACE, span(source, next), //
                "an opening brace must be the last token on the line of the construct that opens its scope; " + text(next) + " is written after it", //
                "end of line", next.getText()));
    }

    /**
     * A body brace that opens a physical line after something that could only have introduced a body.
     * A stand-alone scope block has no introducer, so its brace legitimately opens a line and the
     * introducer test is what keeps the two apart.
     */
    private static void misplacedBodyBrace(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token open = code.get(index);
        Token previous = previous(code, index);
        if (previous == null || previous.getLine() == open.getLine() || !introducesBody(previous.getType())) {
            return;
        }
        found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE, span(source, open), //
                "an opening brace must sit on the physical line of the construct that introduces its scope; the body it opens starts on the next line", //
                "'{' on the line of its construct", "'{' on its own line"));
    }

    /** A `}` shares its line with nothing; a clause keyword after it on one line names itself. */
    private static void closingBrace(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token close = code.get(index);
        Token next = next(code, index);
        if (next != null && next.getLine() == close.getLine() && !closesEnclosingBracket(next.getType())) {
            found.add(Diagnostic.expectedFound(clause(next.getType()) ? DiagnosticCode.PARSER_CLAUSE_NOT_AT_LINE_START //
                    : DiagnosticCode.PARSER_BRACE_SHARES_LINE, span(source, close), //
                    clause(next.getType()) //
                            ? "closing `}` must be on its own physical line; `" + next.getText() + "` must begin the next line" //
                            : "closing `}` must be the only significant token on its physical line; " + text(next) + " follows it on the same line", //
                    "end of line", next.getText()));
            return;
        }
        Token previous = previous(code, index);
        if (previous != null && previous.getLine() == close.getLine() && previous.getType() != SolvikLexer.LBRACE) {
            found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_BRACE_SHARES_LINE, span(source, close), //
                    "closing `}` must be the only significant token on its physical line; " + text(previous) + " precedes it on the same line", //
                    "end of line", previous.getText()));
        }
    }

    /**
     * A three-clause `for` header is a `for` whose parenthesized header holds a `;`. The separators are
     * the ones the language removed, so the diagnostic names the shape rather than letting the parser
     * fail somewhere inside the header.
     */
    private static void threeClauseForHeader(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        int depth = 0;
        for (int i = index + 1; i < code.size(); i++) {
            Token token = code.get(i);
            if (token.getType() == SolvikLexer.LPAREN) {
                depth++;
            } else if (token.getType() == SolvikLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return;
                }
            } else if (depth > 0 && token.getType() == SolvikLexer.SEMI) {
                found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_UNSUPPORTED_FOR_CLAUSES, span(source, code.get(index)), //
                        "the three-clause `for (init; condition; update)` was removed in 2026.11-draft; write the initializer, then a `while` loop whose body ends with the update", //
                        "for (name in range) or while (condition)", "for (clauses separated by ';')"));
                return;
            }
        }
    }

    /**
     * A closing brace may be followed on its line only by the brackets and separator that close what
     * surrounds its body: they cannot begin a statement, so they carry no risk of a second statement
     * reading as one, and no other spelling lets a call argument list close.
     */
    private static boolean closesEnclosingBracket(int type) {
        return type == SolvikLexer.RPAREN || type == SolvikLexer.RBRACKET || type == SolvikLexer.COMMA;
    }

    /**
     * Tokens after which a `{` can only be the body the introducer opened. The keywords listed here
     * cannot end a statement, so a line break before their body brace is always the wrong spelling.
     *
     * <p>A header's `)` is deliberately absent. A scope block statement is legal on the line after any
     * statement, including one that ends in `)`, and the token stream cannot tell `func f(): Unit`
     * followed by its brace from a call followed by a block - only the parse tree can, by asking
     * whether the brace opened the construct's body or a statement of its own. Reporting on `)` here
     * would reject legal programs, which is the worse failure, so that case is left to the parse
     * error a program that needs it already produces.
     */
    private static boolean introducesBody(int type) {
        return type == SolvikLexer.TRY || type == SolvikLexer.ELSE || type == SolvikLexer.STATIC;
    }

    private static boolean clause(int type) {
        return type == SolvikLexer.ELSE || type == SolvikLexer.CATCH || type == SolvikLexer.FINALLY;
    }

    private static List<Token> significant(BufferedTokenStream tokens) {
        List<Token> out = new ArrayList<>();
        for (Token token : tokens.getTokens()) {
            // A line boundary this stage placed is a separator, not code: it names the end of a line
            // rather than a token the programmer wrote, so it can never share a line with anything.
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF && !PhysicalLineTokenSource.isLineBoundary(token)) {
                out.add(token);
            }
        }
        return out;
    }

    private static Token next(List<Token> code, int index) {
        return index + 1 < code.size() ? code.get(index + 1) : null;
    }

    private static Token previous(List<Token> code, int index) {
        return index > 0 ? code.get(index - 1) : null;
    }

    private static String text(Token token) {
        return "`" + token.getText() + "`";
    }

    private static SourceSpan span(SourceFile source, Token token) {
        return SourceSpan.of(source.id(), token.getStartIndex(), token.getStopIndex() + 1);
    }
}
