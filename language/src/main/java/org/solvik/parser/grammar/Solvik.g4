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
// Solvik front-end grammar.
// Generated sources are produced by the `antlr4-maven-plugin` during the build; do not edit
// generated parser output by hand.
//
// The syntax model (docs/LANGUAGE_SPEC.md sections 2, 6, 7, 8, 9, 20):
//
//   * A physical file is a source container, not a namespace. It holds file-level `include`
//     directives, `module Name { ... }` blocks, default-module declarations, and the executable
//     top-level statements that form the implicit entry point. A file may declare zero, one, or
//     many modules; several blocks with the same name - in one file or across included files -
//     contribute to the same module. A module is declaration-only and never nests, and an include
//     is file-level only: it includes source and binds no name.
//   * `func name(...) [: Type] { ... }` declares a function at default-module or module scope.
//     `method name(...) [: Type] { ... }` declares a class method, and an interface member is a
//     `method` signature or a `method` default body. `func` as a class or interface member and
//     `method` outside one are parse errors. A constructor is still named after its class and uses
//     no keyword.
//   * Functions are declarations, never values: there are no function types, no function-valued
//     bindings, parameters, returns or delegates, no anonymous functions, and no bound method
//     references. No production of this grammar admits any of them.
//   * Variables are data with explicit types: `var [mutable] name: Type = expression` binds a
//     local and `var [static] [mutable] name: Type [= expression]` declares a class property. A
//     local initializer never infers a type.
//   * A modifier follows the keyword of the construct it modifies, in one canonical order:
//     `class mutable Name`, `class abstract Name`, `method static name`, `method override name`,
//     `method override mutable name`, `var static name`, `var static mutable name`. A reordered or
//     repeated modifier matches no production.
//   * `delegate name: InterfaceType [= expression]` is its own class-member declaration.
//   * There is no source-level `Unit` type: a callable that writes no `: Type` produces no value.
//
// Statements are separated by physical lines (docs/LANGUAGE_SPEC.md section 16):
//   * the lexer keeps physical newlines as hidden NEWLINE tokens and comments as hidden comment
//     tokens instead of discarding them, so org.solvik.parser.PhysicalLineTokenSource can observe
//     line boundaries and forward one NEWLINE token per boundary on the default channel before
//     the parser ever runs (a combined grammar cannot declare custom channels, so the retained
//     tokens live on HIDDEN);
//   * the grammar requires a separator between two constructs, and `separator` offers the physical
//     line boundary and `;`. A `;` is only ever a separator between two constructs written on one
//     physical line and never a terminator: a `;` that ends a line, ends the file, or precedes a
//     stand-alone closing brace is rejected after parsing by PhysicalLineRules (SOLV-PARS-012),
//     so the grammar stays line-shape free while no line-final `;` is ever accepted;
//   * NULLABLE_DOT ('?.') is lexed as one token so a line cannot break between `?` and `.`.
//
// Retired syntax is matched only by the `removed*` productions at the end of this file. Each of
// them exists so a program written against the previous syntax model is reported with a dedicated
// diagnostic that names the replacement (SOLV-PARS-013, SOLV-PARS-014) instead of a generic
// unexpected-token error; SolvikAstBuilder builds no node for any of them and SolvikParser fails the
// compilation before the builder runs. They carry no executable meaning and must not be extended.
//
// The remaining comments record the token-level facts of the features that are in the language:
// raw strings (section 15), the operator tiers (section 3), classes and objects (section 7), the
// root type hierarchy and single inheritance (section 4), interfaces and default methods
// (section 8), delegation (section 9), generics (section 11), enums, abstract classes and
// exhaustive match (section 12), null safety (sections 5 and 18), the non-fallthrough `switch`
// (section 13), expression-oriented constructs (section 21), and error handling (sections 21.7
// and 23).
grammar Solvik;

@lexer::members {
    /**
     * Whether the input at the current position is the exact closing delimiter for an opening
     * delimiter with {@code hashes} hashes: one '"' followed by exactly that many '#' characters
     * and no further '#'. A quote with a different hash count is content, not a terminator.
     */
    private boolean isRawStringClose(int hashes) {
        if (_input.LA(1) != '"') {
            return false;
        }
        for (int i = 1; i <= hashes; i++) {
            if (_input.LA(1 + i) != '#') {
                return false;
            }
        }
        return _input.LA(1 + hashes + 1) != '#';
    }

    /** Expands the input position by one character, keeping ANTLR's line/column tracking intact. */
    private void consumeRawStringCharacter() {
        ((LexerATNSimulator) getInterpreter()).consume(_input);
    }

    /**
     * Scans the body of a raw string after the rule has matched its contiguous 'r' '#'* '"'
     * opening delimiter. The opening hash count is recovered from the matched text; scanning stops
     * at the first '"' followed by exactly that many '#' characters or at end of input, where an
     * unterminated-literal diagnostic is reported at the opening delimiter. Because the complete
     * literal is consumed as one token, its embedded newlines can never end a line.
     */
    private void lexRawStringBody() {
        int hashes = getText().length() - 2;
        int start = _tokenStartCharIndex;
        int line = _tokenStartLine;
        int column = _tokenStartCharPositionInLine;
        while (true) {
            int c = _input.LA(1);
            if (c == IntStream.EOF) {
                reportUnterminatedRawString(start, line, column, hashes);
                return;
            }
            if (c == '"' && isRawStringClose(hashes)) {
                for (int i = 0; i < hashes + 1; i++) {
                    consumeRawStringCharacter();
                }
                return;
            }
            consumeRawStringCharacter();
        }
    }

    private void reportUnterminatedRawString(int start, int line, int column, int hashes) {
        String expected = "\"" + "#".repeat(hashes);
        int stop = start + hashes + 1;
        Token opening = _factory.create(_tokenFactorySourcePair, RAW_STRING_LITERAL, _input.getText(Interval.of(start, stop)), DEFAULT_TOKEN_CHANNEL, start, stop, line, column);
        getErrorListenerDispatch().syntaxError(this, opening, line, column,
                "unterminated raw string literal; expected closing delimiter " + expected,
                new org.solvik.parser.UnterminatedRawStringException(this, _input, opening, expected));
    }
}

// Implicit `main`: a source file may contain executable statements at the top level, mixed freely
// with declarations. Those statements, in source order, form the body of an implicit
// `func main()`; a file that also declares `main` is a duplicate-declaration error, and a file
// with neither top-level statements nor an explicit `main` is still valid and does nothing. A
// top-level `var`, mutable or not, is therefore a local of the implicit main, not a global.
// Phase 16 adds compile-time `include` (docs/LANGUAGE_SPEC.md section 20): a top-level-only
// directive `include <string literal>` whose target file is parsed and spliced into the program
// before semantic analysis. `include` is reserved so it can no longer be an identifier.
compilationUnit: (includeDecl | moduleBlock | functionDecl separator | classDecl separator | interfaceDecl separator | enumDecl separator | errorDecl separator | statement | separator | removedModuleHeader | removedClassModifierPrefix | removedIncludeAlias)* EOF ;

// A named module: a braced block of declarations that introduces a namespace. A file may hold any
// number of blocks, and blocks with one name - in one file or across included files - contribute to
// the same module. The block is declaration-only and never nests (moduleMember holds no module
// block), so a nested module or an executable statement inside one is a parse error. A block closes
// on its own `}` and needs no trailing separator.
moduleBlock: MODULE Identifier NEWLINE? LBRACE (moduleMember separator | separator)* RBRACE ;

// One declaration inside a named module. `func`, `class`, `interface`, `enum`, and `error` are the
// declarations the default module accepts too; an `include` is a file-level directive and a
// statement belongs to the default module's implicit entry point, so neither is a module member.
moduleMember: functionDecl | classDecl | interfaceDecl | enumDecl | errorDecl | removedClassModifierPrefix ;

// A compile-time include directive. It takes a normal or raw string path and is terminated by its
// line's separator. It is not a statement and may only appear at file level: it includes source, and
// it binds no name, because a module is named by its own `module` block.
includeDecl: INCLUDE (stringLiteral | rawStringLiteral) separator ;

// A callable's return type is optional (docs/LANGUAGE_SPEC.md section 6): a declaration that
// produces a value writes `: Type`, while a declaration that produces no value omits it and has no
// source type at all. The AST carries an empty declared return type for the omitted form, so the
// parser and the semantic layer need no special case. Parameter types remain mandatory.
functionDecl: FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? NEWLINE? block ;

// `class`, optionally followed by exactly one modifier: `class mutable` opens the class to extension
// and `class abstract` makes it non-constructible. Because the modifier follows the keyword and is
// single, `mutable class`, `abstract class`, and `class mutable abstract` match no production.
classDecl: CLASS (ABSTRACT | MUTABLE)? Identifier typeParameterList? (EXTENDS typeRef)? (IMPLEMENTS typeRefList)? NEWLINE? LBRACE (classMember separator | separator)* RBRACE ;

interfaceDecl: INTERFACE Identifier typeParameterList? (EXTENDS typeRefList)? NEWLINE? LBRACE (interfaceMember separator | separator)* RBRACE ;

// An enum declaration (docs/LANGUAGE_SPEC.md section 12). Variants are nested nominal
// constructors that may carry positional values; every variant is terminated by its line's
// separator, because the grammar tolerates a variant list spread across physical lines.
enumDecl: ENUM Identifier typeParameterList? NEWLINE? LBRACE (enumVariant separator | separator)* RBRACE ;
// An `error` declaration is an enum-shaped closed value type whose variants are the concrete error
// values. Its grammar mirrors `enumDecl` so it reuses the enum semantic/lowering machinery; the
// keyword is reserved so it cannot be an identifier.
errorDecl: ERROR Identifier typeParameterList? NEWLINE? LBRACE (errorVariant separator | separator)* RBRACE ;
errorVariant: Identifier (LPAREN typeRefList? RPAREN)? ;

enumVariant: Identifier (LPAREN typeRefList? RPAREN)? ;

interfaceMember: signatureDecl | defaultMethodDecl | removedFuncMember ;

// An abstract interface signature: `method name(...)` with no body, so no scope and no braces; its
// line's separator ends it. Like every callable, its return type is optional: a signature that
// writes no `: Type` declares a method that produces no value.
// `func` is not an interface member, so a `func` written here matches only `removedFuncMember`.
signatureDecl: METHOD Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? ;

defaultMethodDecl: METHOD Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? NEWLINE? block ;

typeRefList: typeRef (NEWLINE* COMMA NEWLINE* typeRef)* ;

classMember: propertyDecl | delegateDecl | constructorDecl | methodDecl | staticBlock | removedFuncMember | removedDelegateVar | removedMemberModifierPrefix ;

// A property: `var [static] [mutable] name: Type [= expression]`. The type annotation is always
// written; a modifier follows `var`, and the single canonical order is `static` then `mutable`, so
// `var mutable static x: Integer` and a repeated modifier match no production. A static property
// belongs to the class rather than to an instance.
propertyDecl: VAR STATIC? MUTABLE? Identifier COLON typeRef (ASSIGN expression)? ;

// A class method: `method [static] [override] [mutable] name(...)`. The keywords `func` and
// `method` keep exactly one meaning each - `method` declares a class or interface member and `func`
// does not - and the canonical modifier order is the order written here. A static method is not
// overridable, which the semantic layer enforces on a static method that also writes `override` or
// `mutable` (SOLV-SEM-047).
methodDecl: METHOD STATIC? OVERRIDE? MUTABLE? Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? NEWLINE? block ;

// A delegate (docs/LANGUAGE_SPEC.md section 9): an immutable, explicitly typed member that the
// compiler forwards unresolved interface members to. `delegate name: InterfaceType` is its own
// declaration - never `delegate var` and never `var delegate` - and the optional initializer is
// permitted because a delegate is initialized under the normal constructor rules. The type
// annotation is required and must name an interface.
delegateDecl: DELEGATE Identifier COLON typeRef (ASSIGN expression)? ;

// The class initializer: a statement list in a block, at most one per class (SOLV-SEM-046). A
// block ends in `}`, which ends its own line, so the block needs no trailing separator and the
// class body's stand-alone separator tolerance covers an explicit one.
staticBlock: STATIC NEWLINE? block ;

// A constructor (docs/LANGUAGE_SPEC.md section 7): a class member named after the enclosing class
// with no `func` keyword and no return type. Calling the class name invokes it. The grammar accepts
// any identifier here; the semantic layer requires it to match the enclosing class name, reports a
// declaration whose name does not match, and rejects a non-constructor member named after the class.
constructorDecl: Identifier LPAREN parameterList? RPAREN NEWLINE? block ;

parameterList: NEWLINE* parameter (NEWLINE* COMMA NEWLINE* parameter)* NEWLINE* ;

parameter: Identifier COLON typeRef ;

// A generic declaration's type parameter list, e.g. `<T>` or `<K, V>`. Bounds are not part of the
// initial language, so each parameter is a bare name.
typeParameterList: LT NEWLINE* Identifier (NEWLINE* COMMA NEWLINE* Identifier)* NEWLINE* GT ;

// A written type: an optional module prefix, a name, optional type arguments, and an optional
// nullable marker. The prefix is a single identifier separated by `::` (Phase 17); `::` is
// deliberately distinct from `.` member access so a module-qualified type can never be confused
// with a member access. The semantic layer decides whether a bare generic name is a raw-type error
// or a declared type parameter.
typeRef: Identifier (COLONCOLON Identifier)? typeArguments? QUESTION? | removedFunctionTypeRef ;

typeArguments: LT NEWLINE* typeRef (NEWLINE* COMMA NEWLINE* typeRef)* NEWLINE* GT ;

// `block` closes a scope, and every brace-delimited body of the language is written in its shape: the
// line breaks a program contains are absorbed here, so a body may be spread over lines or - because no
// line break is demanded anywhere in the rule - written entirely on the line of the construct that
// introduced it. What the rule cannot say is that a `{` must end its line, that a `}` must stand alone,
// and that a clause keyword must begin a line; those three are facts about physical lines rather than
// about phrase structure, and org.solvik.parser.PhysicalLineRules reports them as SOLV-PARS-010,
// SOLV-PARS-007 and SOLV-PARS-008, and SOLV-PARS-009 after the parse succeeds. Writing them into the grammar would need a
// rule per line shape and would spread one rule's content over the whole grammar.
block: LBRACE (statement | separator)* statementCore? RBRACE ;

// The alternatives are one statement each.
// A separator between two statements, declarations, or class/interface members. Section 16 makes the
// physical line boundary the mandatory terminator; `;` appears here only so that two constructs of one
// physical line can be written, and PhysicalLineRules then rejects every `;` that ends a line instead
// of separating two constructs on one. A run of boundaries between constructs is tolerated exactly as
// a blank line is.
separator: NEWLINE | SEMI ;

statement: statementCore separator ;

// A statement without its separator. The option exists at the end of a `block` and a `valueBlock`
// only: it lets a program that writes its last statement on the closing brace's line still parse,
// so the layout stage can name the mistake as the dedicated SOLV-PARS-007 or SOLV-PARS-010
// diagnostic instead of the parser's generic complaint. Every other position demands its boundary.
statementCore: localDecl | ifStmt | whileStmt | forInStmt | removedForStmt | switchStmt | block | breakStmt | continueStmt | returnStmt | throwStmt | tryStmt | exprStmt | removedInferredLocalDecl ;

// Every local writes its type: `var [mutable] name: Type = expression`. The initializer never infers
// a type, so the annotation is required rather than optional (docs/LANGUAGE_SPEC.md section 2).
localDecl: VAR MUTABLE? Identifier COLON typeRef ASSIGN expression ;

// The one binding keyword is `var`; `mutable` follows it to permit reassignment
// (docs/LANGUAGE_SPEC.md section 2). Keyword first, modifier second, so `mutable var` is rejected.
bindingKind: VAR MUTABLE? ;

ifStmt: IF LPAREN expression RPAREN NEWLINE? block (NEWLINE? elseBranch)? ;

elseBranch: ELSE NEWLINE? ifStmt | ELSE NEWLINE? block ;

whileStmt: WHILE LPAREN expression RPAREN NEWLINE? block ;

// The three-clause `for` was removed by the 2026.11 physical-line revision. A statement-shaped
// production matches its header -- its two semicolons belong to the old form and to nothing else in
// the language, since `;` only separates constructs on one line -- so that `SolvikAstBuilder` can
// report the dedicated SOLV-PARS-011 diagnostic at the `for` keyword and name the replacement, the
// same service the reserved-keyword tokens give to removed keywords. The production is parse-only:
// the builder never emits an executable node for it, and the statement is rejected, not reinterpreted.
// docs/LANGUAGE_SPEC.md section 17.
removedForStmt: FOR LPAREN removedForClause? SEMI removedForClause? SEMI removedForClause? RPAREN NEWLINE? block? ;

// One header clause of the removed form: a declaration, or any expression optionally followed by
// `=` and a value, which covers both the initializer and the update of the old syntax; the middle
// clause is the old condition, an expression.
removedForClause: localDecl | removedInferredLocalDecl | assignable ;

// An assignment-shaped clause: an expression optionally followed by `=` and a value. The removed
// three-clause `for` header is shaped from it; an assignment is a statement and never an expression,
// so this shape exists only where the grammar needs it.
assignable: expression (ASSIGN expression)? ;

// A range for-in loop: `for (name in start <op> end) block`. The three range operators are
// distinct tokens, so `..` remains string concatenation outside a for-in header. The loop variable
// is implicitly declared by the semantic layer; bounds are Integer expressions evaluated once.
forInStmt: FOR LPAREN Identifier IN rangeExpr RPAREN NEWLINE? block ;

rangeExpr: expression rangeOperator expression ;

rangeOperator: DOTDOTDOT | DOTDOTLT | DOTDOTGT ;

// Phase 15: `switch` is a statement for value dispatch with no implicit fallthrough. A case body is a
// braced block, so it obeys the same brace and separator rules as any other body: the `{` closes the
// label's line and the `}` stands on its own. Because the body's brace marks the end of the label, the
// label carries no colon: `case 1 {`, `default {`. `default` is a separate alternative so the semantic
// layer can enforce at most one and last. A `regex` case label carries a normal or raw string literal
// pattern.
switchStmt: SWITCH LPAREN expression RPAREN NEWLINE? LBRACE (switchCase | defaultCase | separator)* RBRACE ;

// Phase 18: block, `if`, and `switch` expressions (docs/LANGUAGE_SPEC.md section 21). The
// surface syntax is shared with the statement forms; the syntactic context selects the expression
// node. A value-required block accepts an optional unterminated terminal expression (`valueTail`)
// so `{` newline `42` newline `}` and a `;`-separated equivalent all parse to the same result. The
// AST builder identifies the tail structurally, so both separator spellings are
// interchangeable. An expression `if` retains an optional `else` so a missing `else` can be
// reported with a dedicated diagnostic.
blockExpr: valueBlock ;

ifExpr: IF LPAREN expression RPAREN NEWLINE? valueBlock (NEWLINE? elseExprBranch)? ;

elseExprBranch: ELSE NEWLINE? ifExpr | ELSE NEWLINE? valueBlock ;

switchExpr: SWITCH LPAREN expression RPAREN NEWLINE? LBRACE (valueSwitchCase | valueDefaultCase | separator)* RBRACE ;

// A value-required braced body: ordinary terminated statements followed by an optional
// unterminated terminal expression. The terminal expression is the block or case result.
valueBlock: LBRACE (statement | separator)* valueTail? separator? RBRACE ;

valueTail: expression ;

valueSwitchCase: CASE caseLabel (NEWLINE* COMMA NEWLINE* caseLabel)* NEWLINE? valueBlock ;

valueDefaultCase: DEFAULT NEWLINE? valueBlock ;

switchCase: CASE caseLabel (NEWLINE* COMMA NEWLINE* caseLabel)* NEWLINE? block ;

defaultCase: DEFAULT NEWLINE? block ;

caseLabel: regexCaseLabel | expression ;

regexCaseLabel: REGEX_KW (stringLiteral | rawStringLiteral) ;

breakStmt: BREAK ;

continueStmt: CONTINUE ;

returnStmt: RETURN expression? ;

// Error handling phases. A `throw` terminates the enclosing scope; a try statement groups an
// operation with zero or more catch clauses and at most one finally clause (section 21.9).
throwStmt: THROW expression ;

tryStmt: TRY NEWLINE? block (NEWLINE? catchClause)* (NEWLINE? finallyClause)? ;

catchClause: CATCH LPAREN Identifier COLON typeRef RPAREN NEWLINE? block ;

finallyClause: FINALLY NEWLINE? block ;

exprStmt: expression (ASSIGN expression)? ;

expression: nullCoalescing ;

// Phase 10: `??` is the lowest-precedence binary operator (docs/LANGUAGE_SPEC.md section 3).
nullCoalescing: logicalOr (NEWLINE* NULL_COALESCE NEWLINE* logicalOr)* ;

logicalOr: logicalAnd (OR NEWLINE* logicalAnd)* ;

logicalAnd: equality (AND NEWLINE* equality)* ;

equality: relational ((EQ | NEQ | EQEQ | NEQEQ) NEWLINE* relational)* ;

// A binary expression tier for string concatenation `..`, which binds looser than arithmetic but
// tighter than comparison (docs/LANGUAGE_SPEC.md section 3). Each operand is rendered through
// `toString`.
relational: concat relation* ;

// Phase 10: `is` and `as` share the ordering tier, but their right operand is a written type rather
// than an expression. A separate `relation` alternative keeps the left-associative fold explicit.
relation: (LT | LE | GT | GE) NEWLINE* concat | IS NEWLINE* typeRef | AS NEWLINE* typeRef ;

concat: additive (DOTDOT NEWLINE* additive)* ;

additive: multiplicative ((ADD | SUB) NEWLINE* multiplicative)* ;

multiplicative: unary ((MUL | DIV) NEWLINE* unary)* ;

unary: (BANG | SUB) NEWLINE* unary | postfix ;

postfix: primary suffix* ;

primary: literal | paren | thisExpr | superExpr | matchExpr | ifExpr | switchExpr | blockExpr | removedAnonymousFunctionExpr | name ;

// Phase 13: `match` is expression-oriented and exhaustive for a known closed variant set. A branch
// result ends its line, which the enclosing branch list consumes as a separator.
matchExpr: MATCH expression NEWLINE? LBRACE (matchBranch | separator)* RBRACE ;

matchBranch: pattern ARROW NEWLINE* expression ;

pattern: Identifier (COLON typeRef | LPAREN NEWLINE* patternList? NEWLINE* RPAREN)? ;

patternList: pattern (NEWLINE* COMMA NEWLINE* pattern)* ;

paren: LPAREN NEWLINE* expression NEWLINE* RPAREN ;

thisExpr: THIS ;

superExpr: SUPER ;

name: Identifier ;

// Phase (error handling): a postfix `?` unwraps a Result value, propagating its Err through the
// enclosing Result-returning boundary. It chains with member access and calls so that
// `File.read(path).context("...")?` is one postfix sequence. Distinct from the nullable type suffix
// in `typeRef`; the two occupy different grammar positions.
suffix: memberSuffix | namespaceSuffix | callSuffix | propagationSuffix ;
propagationSuffix: QUESTION ;

memberSuffix: NEWLINE* (DOT | NULLABLE_DOT) Identifier ;

// A module-qualified name `prefix::name`. `::` is the namespace separator and is distinct from the
// `.` member-access operator, so a qualified reference is unambiguous without name-based
// heuristics. It chains, so `math::Result::Ok` is a valid path.
namespaceSuffix: NEWLINE* COLONCOLON Identifier ;

callSuffix: typeArguments? LPAREN NEWLINE* argumentList? NEWLINE* RPAREN ;

// A call argument. A `key: value` entry names a key/value pair and is meaningful only in a
// built-in `Map` construction; the semantic pass rejects an entry in any other argument list.
// A trailing comma is permitted after the last argument (for example a multiline argument list
// that ends in `,\n)`), and it contributes no argument. A list still requires at least one
// argument, so `f(,)` is a parse error; `f()` takes the empty path through `callSuffix` instead.
argumentList: callArgument (NEWLINE* COMMA NEWLINE* callArgument)* COMMA? ;
callArgument: expression (COLON expression)? ;

literal: integerLiteral | longLiteral | floatingLiteral | boolLiteral | characterLiteral | stringLiteral | rawStringLiteral | nullLiteral ;

integerLiteral: INTEGER_LITERAL ;

longLiteral: LONG_LITERAL ;

floatingLiteral: FLOATING_LITERAL ;

boolLiteral: BOOL_LITERAL ;

characterLiteral: CHARACTER_LITERAL ;

stringLiteral: STRING_LITERAL ;

rawStringLiteral: RAW_STRING_LITERAL ;

// Phase 10: `null` is a keyword literal, not an identifier.
nullLiteral: NULL ;

// `func` declares a function at default-module or module scope, and `method` declares a class or
// interface member. Neither ends a line: each opens a callable declaration whose name follows.
// --- Retired syntax ----------------------------------------------------------------------------
// The productions below match only syntax the current revision retired. Each exists so
// `SolvikParser` can report a dedicated, source-located diagnostic that names the replacement
// instead of a generic unexpected-token error. `SolvikAstBuilder` builds no node for any of them,
// and the parse fails before the builder runs. They must not be extended or reinterpreted.

// `func` written as a class or interface member. `method` declares members; `func` is not a member
// keyword.
removedFuncMember: FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? NEWLINE? block? ;

// A local declaration that writes no type. Every local writes `: Type`.
removedInferredLocalDecl: VAR MUTABLE? Identifier ASSIGN expression ;

// `delegate var name: InterfaceType`. A delegate is its own declaration: `delegate name: InterfaceType`.
removedDelegateVar: DELEGATE VAR Identifier (COLON typeRef)? (ASSIGN expression)? ;

// A file-level module header. A module is a braced block: `module Name { ... }`.
removedModuleHeader: MODULE Identifier separator ;

// An `include ... alias name` suffix. An include includes source and binds no name.
removedIncludeAlias: INCLUDE (stringLiteral | rawStringLiteral) ALIAS Identifier separator ;

// A class modified before its keyword. A modifier follows the construct keyword: `class mutable`,
// `class abstract`.
removedClassModifierPrefix: (ABSTRACT | MUTABLE) CLASS Identifier typeParameterList? (EXTENDS typeRef)? (IMPLEMENTS typeRefList)? NEWLINE? LBRACE (classMember separator | separator)* RBRACE ;

// A member modified before its declaration keyword. A modifier follows the construct keyword:
// `method static`, `method override`, `method mutable`, `var static`, `var static mutable`.
removedMemberModifierPrefix: (STATIC | MUTABLE | OVERRIDE)+ removedMemberDeclaration ;

removedMemberDeclaration: VAR Identifier COLON typeRef (ASSIGN expression)?
    | FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? NEWLINE? block ;

// An anonymous function expression, with or without the retired capture list. Functions are
// declarations, not values.
removedAnonymousFunctionExpr: FUNC (LBRACKET NEWLINE* removedCaptureItemList NEWLINE* RBRACKET)? LPAREN parameterList? RPAREN (COLON typeRef)? NEWLINE? block ;

// The retired capture list items. A capture list belonged to an anonymous function and names one
// binding or `this` per item.
removedCaptureItemList: removedCaptureItem (NEWLINE* COMMA NEWLINE* removedCaptureItem)* ;

removedCaptureItem: Identifier | THIS ;

// A function type reference. Functions are declarations, not values.
removedFunctionTypeRef: FUNC LPAREN NEWLINE* typeRefList? NEWLINE* RPAREN (COLON typeRef)? ;

FUNC: 'func' ;
METHOD: 'method' ;
// `static` is the class-level member modifier and the class initializer keyword
// (docs/LANGUAGE_SPEC.md section 7). It follows the declaration keyword it modifies
// (`method static`, `var static`) and opens a block as the initializer. It never ends a line.
STATIC: 'static' ;
// Phase 16: `include` introduces a compile-time file inclusion; reserved so it cannot be an identifier.
INCLUDE: 'include' ;
// `module` opens a named-module block and is reserved so it cannot be an identifier. It ends no
// line: it opens a construct and must be followed by its name.
MODULE: 'module' ;
// The include-alias suffix was removed with the file-module header syntax. `alias` stays reserved so
// a program written against the old revision fails at the keyword with the replacement named
// (SOLV-PARS-006) rather than silently reinterpreting it as an identifier.
ALIAS: 'alias' ;
CLASS: 'class' ;
INTERFACE: 'interface' ;
// Phase 12: `enum` introduces a closed set of value-carrying variants. `abstract` marks a class that
// cannot be constructed and exists to be extended (docs/LANGUAGE_SPEC.md section 12). The 2026.11-draft
// revision removes the keyword `sealed` it replaced; `SEALED` below stays a reserved token.
ENUM: 'enum' ;
// Error-handling phases: `error` introduces a closed nominal value-carrying error type. Like an
// enum it opens a construct, so it ends no line; its variants already
// end in an identifier or a closing paren, both of which terminate.
// `mutable` is the language's single unlock marker: `var mutable` for a writable binding,
// `class mutable` for an extendable class, `method mutable` for an overridable method, and
// `var mutable`/`method override mutable` for a writable property or method. It follows the keyword
// of the construct it modifies and is never a newline terminator.
MUTABLE: 'mutable' ;
ABSTRACT: 'abstract' ;
// Reserved tokens with no parser production (docs/LANGUAGE_SPEC.md, "Lexical basics"). They are the
// keywords removed by 2026.11-draft, kept reserved so a program written against the old revision fails
// with SOLV-PARS-006 at the keyword itself rather than silently reinterpreting it as an identifier.
// `SolvikErrorListener#REMOVED_KEYWORDS` maps each to its replacement. They are not dead code and must
// not be deleted: removing a token here would make the spelling a legal identifier.
SEALED: 'sealed' ;
OPEN: 'open' ;
VAL: 'val' ;
DELEGATE: 'delegate' ;
IMPLEMENTS: 'implements' ;
EXTENDS: 'extends' ;
OVERRIDE: 'override' ;
THIS: 'this' ;
SUPER: 'super' ;
// The one binding keyword (docs/LANGUAGE_SPEC.md section 2). It opens a declaration and is never a
// newline terminator.
VAR: 'var' ;
IF: 'if' ;
ELSE: 'else' ;
WHILE: 'while' ;
FOR: 'for' ;
// The range for-in keyword. Reserved so `in` can never be an identifier.
IN: 'in' ;
BREAK: 'break' ;
CONTINUE: 'continue' ;
RETURN: 'return' ;
// Error handling phases: `throw`, `try`, `catch`, and `finally`. None ends a line,
// because a throw already ends in its operand (a value) and the other three open a
// construct that cannot be closed by a bare line boundary.
THROW: 'throw' ;
TRY: 'try' ;
CATCH: 'catch' ;
FINALLY: 'finally' ;
// Phase 13: `match` introduces the exhaustive match expression and `=>` separates a branch's
// pattern from its result.
MATCH: 'match' ;
ARROW: '=>' ;
// Phase 15: the non-fallthrough `switch` statement. `switch`, `case`, and `default` open a
// construct, and `regex` is followed by its pattern literal, so none of the four ends a line.
SWITCH: 'switch' ;
CASE: 'case' ;
DEFAULT: 'default' ;
REGEX_KW: 'regex' ;
// Phase 10 null-safety keywords.
NULL: 'null' ;
IS: 'is' ;
AS: 'as' ;
BOOL_LITERAL: 'true' | 'false' ;
Identifier: [a-zA-Z_][a-zA-Z0-9_]* ;
INTEGER_LITERAL: [0-9]+ ;
// Phase 7: `L`/`l` suffix selects Long; longest-match keeps `123L` distinct from `123`.
LONG_LITERAL: [0-9]+ [Ll] ;
// Phase 7: decimal floating-point literals with an optional exponent. A trailing `f`/`F` selects
// Float; otherwise the literal has type Double.
FLOATING_LITERAL: [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)? [fF]?
               | [0-9]+ [eE] [+-]? [0-9]+ [fF]?
               ;
// Phase 7: a character literal contains exactly one Unicode scalar value or one escape; the
// semantic layer validates the escape set and reports an empty or multi-character literal.
CHARACTER_LITERAL: '\'' (~['\\\r\n] | '\\' .) '\'' ;
STRING_LITERAL: '"' (~["\\\r\n] | '\\' .)* '"' ;
LPAREN: '(' ;
RPAREN: ')' ;
LBRACE: '{' ;
RBRACE: '}' ;
SEMI: ';' ;
ASSIGN: '=' ;
COLON: ':' ;
// `::` is the module/namespace separator. It is lexed as one token so it is never read as two `:`.
COLONCOLON: '::' ;
COMMA: ',' ;
DOT: '.' ;
// `..` is string concatenation. The three-character range operators are before it; ANTLR's
// longest-match rule keeps `...`, `..<`, and `..>` distinct from `..` and from a floating literal
// such as `1.0`.
DOTDOT: '..' ;
DOTDOTDOT: '...' ;
DOTDOTLT: '..<' ;
DOTDOTGT: '..>' ;
// Lexed as one token so a line cannot break between `?` and `.` (Phase 2); Phase 10 makes it the
// safe member-access operator as well.
NULLABLE_DOT: '?.' ;
// Phase 10: `??` is null coalescing; it is lexed as one token so it is never read as `T?` plus `?`.
NULL_COALESCE: '??' ;
// Phase 10: `?` marks a nullable type reference.
QUESTION: '?' ;
// Lexed for the retired anonymous-function capture list, which only `removedAnonymousFunctionExpr`
// consumes. Bracket indexing is not in the language, so `[` and `]` appear in no other production.
LBRACKET: '[' ;
RBRACKET: ']' ;
ADD: '+' ;
SUB: '-' ;
MUL: '*' ;
DIV: '/' ;
BANG: '!' ;
EQEQ: '===' ;
NEQEQ: '!==' ;
EQ: '==' ;
NEQ: '!=' ;
LT: '<' ;
LE: '<=' ;
GT: '>' ;
GE: '>=' ;
AND: '&&' ;
OR: '||' ;
// Phase 2: whitespace keeps every physical newline as a hidden NEWLINE token; '\r\n', '\r', and
// '\n' are all line terminators (matching SourceFile's line model). Comment bodies are hidden
// rather than skipped because a newline inside a block comment is still a physical newline.
WS: [ \t\u000B\u000C]+ -> skip ;
NEWLINE: ('\r\n' | '\r' | '\n') -> channel(HIDDEN) ;
LINE_COMMENT: '//' ~[\r\n]* -> channel(HIDDEN) ;
BLOCK_COMMENT: '/*' .*? '*/' -> channel(HIDDEN) ;

// Phase 3: the counted raw-string delimiter is owned by the hand-written helper above. The rule
// matches the contiguous 'r' '#'* '"' opening delimiter and its action scans the body so the
// complete literal is emitted as one token; embedded newlines therefore never end a line.
RAW_STRING_LITERAL: 'r' '#'* '"' { lexRawStringBody(); } ;
