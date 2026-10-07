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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DelegateDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.parser.PhysicalLineTokenSource;
import org.solvik.parser.generated.SolvikLexer;

/**
 * Grammar-level tests for the class- and member-level keyword vocabulary.
 *
 * <p>The overhaul leaves exactly three declaration modifiers in these positions -- {@code abstract}
 * on a class, {@code mutable} on a class, and {@code mutable} on a method -- where the language
 * previously offered six spellings. Two properties matter and regress independently: the set of
 * accepted modifier combinations, and the fact that the two new words are <em>keywords</em> rather
 * than contextual identifiers, because a word the lexer fails to declare still parses happily as an
 * ordinary name and changes meaning somewhere far away from the parse.
 *
 * <p>The accepted-combination half is asserted on the delivered AST rather than on parse success
 * alone, so a grammar that tolerated both class modifiers and quietly dropped one fails here instead
 * of producing a class whose lock state is not the one its source asked for.
 */
public final class SolvikClassModifierGrammarTest {

    private static ClassDeclNode onlyClass(CompilationUnitNode unit) {
        assertThat(unit.declarations()).hasSize(1);
        return (ClassDeclNode) unit.declarations().get(0);
    }

    private static ClassDeclNode classOf(String name, String source) {
        return onlyClass(parseOk(name, source));
    }

    /** Delivered token stream of one source, through the same physical-line stage the parser reads. */
    private static List<Token> delivered(String source) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream stream = new CommonTokenStream(new PhysicalLineTokenSource(lexer));
        stream.fill();
        return stream.getTokens();
    }

    /** Token names, EOF included. */
    private static List<String> names(String source) {
        return delivered(source).stream()
                .map(token -> SolvikLexer.VOCABULARY.getSymbolicName(token.getType()))
                .toList();
    }

    /** Token spellings, EOF excluded. */
    private static List<String> spellings(String source) {
        return delivered(source).stream()
                .map(Token::getText)
                .filter(text -> !"<EOF>".equals(text))
                .toList();
    }

    // --- class-level modifiers -------------------------------------------------------------

    @Test
    public void anUnmarkedClassIsNeitherAbstractNorMutable() {
        ClassDeclNode decl = classOf("plain.sol", "class Widget {\n}\n");
        assertThat(decl.isAbstract()).isFalse();
        assertThat(decl.isMutable()).isFalse();
    }

    @Test
    public void aMutableClassIsMarkedMutableAndNotAbstract() {
        ClassDeclNode decl = classOf("mutable.sol", "class mutable Widget {\n}\n");
        assertThat(decl.isMutable()).isTrue();
        assertThat(decl.isAbstract()).isFalse();
    }

    @Test
    public void anAbstractClassIsMarkedAbstractAndNotMutable() {
        ClassDeclNode decl = classOf("abstract.sol", "class abstract Widget {\n}\n");
        assertThat(decl.isAbstract()).isTrue();
        assertThat(decl.isMutable()).isFalse();
    }

    /**
     * The two class modifiers cannot be combined, in either order, and the grammar says so.
     *
     * <p>A grammar that forbade only one order would leave the other to be settled by whichever
     * modifier the semantic layer happened to read first, and a class that is simultaneously locked
     * and unlocked has no defined lock state to settle on. A parse error is the right report: the
     * combination is not something the language has a meaning for, so there is no single diagnostic
     * with a message worth giving, and the sequence of class-declaration tokens in the failure is
     * what names the problem.
     */
    @Test
    public void theTwoClassModifiersCannotBeCombinedInEitherOrder() {
        parseFails("both.sol", "class abstract mutable Widget {\n}\n");
        parseFails("both.sol", "class mutable abstract Widget {\n}\n");
    }

    /**
     * {@code abstract} marks a class only, never a member.
     *
     * <p>Bodiless methods are a deferred feature, not part of this vocabulary change, so the word
     * must not be reachable in a method position in the meantime: accepting it would advertise an
     * obligation the compiler cannot yet check.
     */
    @Test
    public void abstractIsRejectedOnAFunction() {
        parseFails("abs.sol", "abstract func f(): Integer {\n    return 1\n}\n");
    }

    // --- member-level modifiers ------------------------------------------------------------

    @Test
    public void aMutableMethodIsMarkedMutable() {
        ClassDeclNode decl = classOf("m.sol",
                "class Widget {\n    method mutable unlocked(): Integer {\n        return 1\n    }\n}\n");
        assertThat(decl.methods()).hasSize(1);
        assertThat(decl.methods().get(0).isMutable()).isTrue();
    }

    @Test
    public void anUnmarkedMethodIsNotMutable() {
        ClassDeclNode decl = classOf("m.sol",
                "class Widget {\n    method locked(): Integer {\n        return 1\n    }\n}\n");
        assertThat(decl.methods().get(0).isMutable()).isFalse();
    }

    /**
     * {@code override} and {@code mutable} are separate facts about a method.
     *
     * <p>Overriding does not re-open the chain; re-opening it is the separate act of marking the
     * override {@code mutable}. Collapsing the two would make every override extendable, which is
     * what the old {@code open override} spelling meant and what the overhaul split apart.
     */
    @Test
    public void anOverrideIsNotItselfMutableButAnOverrideMarkedMutableIs() {
        String base = "class mutable Base {\n    method mutable name(): Integer {\n        return 1\n    }\n}\n";
        // An override only exists below a mutable supertype, so the subclass is the *second*
        // declaration of a two-declaration program and `onlyClass` cannot be used to read it.
        ClassDeclNode closedChild = (ClassDeclNode) parseOk("closed.sol", base
                + "class Child extends Base {\n    method override name(): Integer {\n        return 2\n    }\n}\n")
                .declarations().get(1);
        FunctionDeclNode closed = closedChild.methods().get(0);
        assertThat(closed.isOverride()).isTrue();
        assertThat(closed.isMutable()).isFalse();

        ClassDeclNode reopening = (ClassDeclNode) parseOk("open.sol", base
                + "class Child extends Base {\n    method override mutable name(): Integer {\n        return 2\n    }\n}\n")
                .declarations().get(1);
        FunctionDeclNode reopened = reopening.methods().get(0);
        assertThat(reopened.isOverride()).isTrue();
        assertThat(reopened.isMutable()).isTrue();
    }

    /**
     * A delegate may not be marked {@code mutable}, at the grammar rather than semantically.
     *
     * <p>A delegate is an immutable forwarding property by definition, so {@code delegate var mutable}
     * has no reading in which the {@code mutable} does anything. Rejecting it while parsing is
     * what keeps the delegate node from ever having to represent the combination.
     */
    @Test
    public void aDelegateMayNotBeDeclaredMutable() {
        parseFails("d.sol", "interface Named {\n}\nclass Widget {\n    delegate  mutable shared: Named\n}\n");
    }

    /** A delegate is a declaration of its own, with no binding marker to read. */
    @Test
    public void aDelegateDeclaresNoBindingMarkerOfItsOwn() {
        CompilationUnitNode unit = parseOk("d.sol",
                "interface Named {\n}\nclass Widget {\n    delegate  shared: Named\n}\n");
        ClassDeclNode decl = (ClassDeclNode) unit.declarations().get(1);
        List<DelegateDeclNode> delegates = decl.delegates();
        assertThat(delegates).hasSize(1);
        assertThat(delegates.get(0).name()).isEqualTo("shared");
    }

    /** A {@code static var mutable} property carries both markers through to the AST. */
    @Test
    public void aStaticMutablePropertyIsDeliveredAsBothStaticAndMutable() {
        ClassDeclNode decl = classOf("s.sol", "class Widget {\n    var static mutable count: Integer = 1\n}\n");
        List<PropertyDeclNode> statics = decl.staticProperties();
        assertThat(statics).hasSize(1);
        assertThat(statics.get(0).bindingKind()).isEqualTo(BindingKind.MUTABLE);
        assertThat(decl.properties()).isEmpty();  // the property is static, not an instance property
    }

    /** A plain {@code var} property is immutable, and {@code var mutable} is not. */
    @Test
    public void aPropertyBindingMarkerDistinguishesImmutableFromMutable() {
        ClassDeclNode decl = classOf("p.sol",
                "class Widget {\n    var frozen: Integer = 1\n    var mutable moving: Integer = 2\n}\n");
        assertThat(decl.properties()).hasSize(2);
        assertThat(decl.properties().get(0).bindingKind()).isEqualTo(BindingKind.IMMUTABLE);
        assertThat(decl.properties().get(1).bindingKind()).isEqualTo(BindingKind.MUTABLE);
    }

    // --- the new words really are keywords -------------------------------------------------

    /**
     * {@code mutable} and {@code abstract} are declared lexer keywords, not contextual identifiers.
     *
     * <p>This is the failure mode an AST assertion cannot see: had either word been added to the
     * grammar as an anonymous literal without a lexer rule, it would still be lexed as an
     * {@code Identifier}, the parser would still match it, and every {@code isMutable} accessor would
     * then be reading a name rather than a marker. The prefix cases are the other half: a keyword
     * declared so that it matches the beginning of a longer word would split {@code mutableval} into
     * two tokens and silently break an existing program.
     */
    @Test
    public void theNewKeywordsLexAsKeywordsAndTheirPrefixesStayIdentifiers() {
        assertThat(names("var mutable x: Integer = 1\n")).startsWith("VAR", "MUTABLE");
        // The declaration keyword comes first and the modifier follows it.
        assertThat(names("class abstract Widget {\n}\n")).startsWith("CLASS", "ABSTRACT");
        assertThat(names("var mutableval: Integer = 1\n")).startsWith("VAR", "Identifier");
        assertThat(names("var abstractly: Integer = 1\n")).startsWith("VAR", "Identifier");
    }

    /** The last arm above is a contradiction unless {@code mutable} is a keyword. */
    @Test
    public void mutableIsNotUsableAsAnIdentifier() {
        parseFails("shadow.sol", "var mutable = 1\n");
        parseFails("shadow.sol", "var abstract = 1\n");
    }

    /** The declaration keyword comes first, so a modifier-first binding declaration is rejected. */
    @Test
    public void aModifierFirstBindingDeclarationIsRejected() {
        parseFails("prefix.sol", "mutable var x: Integer = 1\n");
        parseFails("prefix.sol", "val x: Integer = 1\n");
    }

    /**
     * A modifier at a line end does not end its line.
     *
     * <p>Boundary placement is purely lexical, and {@code mutable} followed by a newline is the one shape
     * where a "line ending in a word ends its line" rule would fire on a marker still waiting for
     * the declaration it modifies. A line boundary there would split the declaration in two, and
     * because placement happens before the parser sees anything, the resulting error would be reported
     * far from its cause.
     */
    @Test
    public void aModifierAtALineEndDoesNotEndItsLine() {
        assertThat(spellings("mutable\n")).containsExactly("mutable");
        assertThat(spellings("abstract\n")).containsExactly("abstract");
    }

    /** The declaration still parses as one unit when its modifier sits on its own line. */
    @Test
    public void aDeclarationWhoseModifierEndsALineIsStillOneDeclaration() {
        ClassDeclNode decl = classOf("nl.sol",
                "class Widget {\n    method mutable f(): Integer {\n        return 1\n    }\n}\n");
        assertThat(decl.methods()).hasSize(1);
        assertThat(decl.methods().get(0).isMutable()).isTrue();
    }

    /**
     * An ordinary statement at a line end still terminates.
     *
     * <p>Without this arm the previous test would be satisfied by a boundary stage that placed
     * nothing at all -- a regression the rest of the suite would report as a wave of unrelated parse
     * errors rather than as the specific thing that broke.
     */
    @Test
    public void anOrdinaryStatementAtALineEndStillTerminates() {
        assertThat(names("var x: Integer = 1\nvar y: Integer = 2\n")).contains("NEWLINE");
    }
}
