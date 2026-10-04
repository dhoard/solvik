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
 * state: an opening brace is the last token on the line of the construct that introduces its scope,
 * and a closing brace stands alone on its line - the only things that may share that line are
 * whitespace and comments. A clause keyword ({@code else}, {@code catch}, {@code finally}) therefore
 * begins its own line, a body brace never begins one, and an empty body is written as an opening
 * brace on one line and a closing brace on the next rather than as {@code {}}. The same scan states
 * the semicolon rule once: a {@code ;} separates two constructs written on one physical line, so a
 * {@code ;} followed by another physical line, end of file, or the stand-alone closing brace of the
 * enclosing scope is rejected at the semicolon itself.
 *
 * <p>These are facts about physical lines, not about phrase structure: {@code Solvik.g4} absorbs the
 * line breaks a program writes so that a body can be read at all, which means a wrongly placed brace
 * still parses, and a rule written in the grammar would have to duplicate every body production once
 * per line shape. Checking the token sequence instead states each rule once, locates it exactly, and
 * leaves the grammar free to describe what a program means.
 *
 * <p>Because the rule holds everywhere, a bracketed body closes its brackets on their own lines:
 * {@code f(}, the body, {@code )}. That is the same fact the closing-brace rule already states - a
 * {@code )} written after a {@code }} shares the brace's line - so no separate bracket rule exists.
 *
 * <p>The stage runs after a successful parse and reads only the default-channel token sequence - the
 * same sequence the parser saw, with comments and blank lines removed - so it never contradicts the
 * parser and never reports on text inside a comment or a literal. Because the line-boundary stream
 * already decided which breaks end a line, a token pair on one physical line here means the programmer
 * wrote them on one physical line.
 *
 * <p>One omission is deliberate and stated rather than hidden. A stand-alone scope block writes its
 * {@code &#123;} as the first token of its line, which is legal, so a brace that opens a line is
 * reported only when the token before it could have introduced a body - {@code try}, {@code else},
 * {@code static}, or a {@code case} label's last token. A header's {@code )}, by contrast, can legally
 * be followed by a scope block on the next line, and only the parse tree can tell that block from a
 * declaration's body, so that case is left to the parse error a program that needs it already
 * produces.
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
            } else if (token.getType() == SolvikLexer.SEMI) {
                separatingSemi(source, code, i, found);
            }
        }
        return found;
    }

    /**
     * A {@code ;} separates two constructs written on one physical line and never terminates one.
     * It is therefore wrong wherever the next thing in the significant token sequence is another
     * physical line, end of file, or the stand-alone closing brace of the enclosing scope: in each
     * case the {@code ;} closed the construct it should only have separated from a next one.
     */
    private static void separatingSemi(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token semi = code.get(index);
        Token after = next(code, index);
        boolean endsConstruct = after == null || after.getLine() != semi.getLine() || after.getType() == SolvikLexer.RBRACE;
        if (endsConstruct) {
            found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_SEMI_ENDS_LINE, span(source, semi), //
                    "`;` separates two constructs written on the same physical line; it does not terminate one, and none follows it on its line", //
                    "a construct on the same line", after == null ? "end of file" : text(after))); 
        }
    }

    /** Nothing but a comment or whitespace may follow an opening brace on its line. */
    private static void openingBrace(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token next = next(code, index);
        if (next == null || next.getLine() != code.get(index).getLine()) {
            return;
        }
        found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_CONTENT_AFTER_OPEN_BRACE, span(source, next), //
                "an opening brace must be the last token on the line of the construct that opens its scope; " + text(next) + " is written after it", //
                "end of line", next.getText()));
    }

    /**
     * A body brace that opens a physical line after something that could only have introduced a body.
     * A stand-alone scope block has no introducer, so its brace legitimately opens a line and the
     * introducer test is what keeps the two apart. A {@code case} label ends with an expression, so
     * for a case line the introducer is found at the start of the label's line rather than in the
     * single token before the brace.
     */
    private static void misplacedBodyBrace(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token open = code.get(index);
        Token previous = previous(code, index);
        if (previous == null || previous.getLine() == open.getLine() //
                || previous.getType() == SolvikLexer.LBRACE //
                || !(introducesBody(previous.getType()) || beginsLabelLine(code, index - 1))) {
            return;
        }
        found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE, span(source, open), //
                "an opening brace must sit on the physical line of the construct that introduces its scope; the body it opens starts on the next line", //
                "'{' on the line of its construct", "'{' on its own line"));
    }

    /**
     * Whether the physical line holding {@code position} - the token before a line-initial brace -
     * begins with {@code case} or {@code default}. The label of such a line is an expression, so the
     * token before the brace is a literal or a name and only the line's first token names the
     * construct. A label continued over several lines is matched on its last line only, which is the
     * shape a misplaced brace follows.
     */
    private static boolean beginsLabelLine(List<Token> code, int position) {
        int line = code.get(position).getLine();
        int first = position;
        while (first > 0 && code.get(first - 1).getLine() == line) {
            first--;
        }
        int type = code.get(first).getType();
        return type == SolvikLexer.CASE || type == SolvikLexer.DEFAULT;
    }

    /**
     * A `}` shares its physical line with nothing: the token before it must be on an earlier line and
     * the token after it on a later one. A clause keyword after the brace names itself, because the
     * program is writing a clause the brace rule would otherwise report as a stray token.
     */
    private static void closingBrace(SourceFile source, List<Token> code, int index, List<Diagnostic> found) {
        Token close = code.get(index);
        Token next = next(code, index);
        if (next != null && next.getLine() == close.getLine()) {
            found.add(Diagnostic.expectedFound(clause(next.getType()) ? DiagnosticCode.PARSER_CLAUSE_NOT_AT_LINE_START //
                    : DiagnosticCode.PARSER_BRACE_SHARES_LINE, span(source, close), //
                    clause(next.getType()) //
                            ? "closing `}` must be on its own physical line; `" + next.getText() + "` must begin the next line" //
                            : "closing `}` must be the only significant token on its physical line; " + text(next) + " follows it on the same line", //
                    "end of line", next.getText()));
            return;
        }
        // A `}` written on its brace's own line is already named by the opening-brace rule, which
        // reports the token after `{`; reporting it twice would split one mistake over two codes.
        Token previous = previous(code, index);
        if (previous != null && previous.getLine() == close.getLine() && previous.getType() != SolvikLexer.LBRACE) {
            found.add(Diagnostic.expectedFound(DiagnosticCode.PARSER_BRACE_SHARES_LINE, span(source, close), //
                    "closing `}` must be the only significant token on its physical line; " + text(previous) + " precedes it on the same line", //
                    "end of line", previous.getText()));
        }
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
        return type == SolvikLexer.TRY || type == SolvikLexer.ELSE || type == SolvikLexer.STATIC //
                || type == SolvikLexer.CASE || type == SolvikLexer.DEFAULT || type == SolvikLexer.ARROW;
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
