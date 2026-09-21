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
// Generated sources are produced by generate_parser.sh; do not edit generated files by hand.
//
// Phase 1 supported constructs: top-level `func` declarations with typed parameters and explicit
// return types; blocks; `val`/`var` locals with initializers and `;` termination;
// call-expression statements; integer, Boolean, and normal-string literals; name references,
// ordinary member access, calls, parentheses, and `+`, `-`, `*`, `/`; `if`/`else`; `return`.
// SimpleLanguage `function` is intentionally not accepted.
//
// Phase 2 adds Go-style lexical semicolon insertion (docs/LANGUAGE_SPEC.md section 16):
//   * the lexer keeps physical newlines as hidden NEWLINE tokens and comments as hidden comment
//     tokens instead of discarding them, so org.solvik.parser.SemicolonInsertingTokenSource can
//     observe line boundaries and inject synthetic SEMI tokens before the parser ever runs
//     (combined grammars cannot declare custom channels, so all three live on HIDDEN);
//   * explicit `;` and synthesized `;` share this grammar's single SEMI token type;
//   * statement lists therefore tolerate redundant stand-alone SEMI tokens (insertion is purely
//     lexical and cannot know whether a statement is already explicitly terminated); stand-alone
//     SEMIs produce no AST nodes;
//   * NULLABLE_DOT ('?.') is lexed as one token so insertion can suppress termination before it.
//     Nullable member access itself is not grammar until Phase 10, so '?.' is lexed and then
//     rejected by the parser.
//   * LBRACKET/RBRACKET exist for the mirror-image reason on the other side of the algorithm:
//     specification condition 1 tracks unmatched '[' and ']' depth. No rule consumes them until
//     bracket syntax lands in a later phase, so programs using them are lexed and then rejected
//     by the parser.
//
// Phase 3 adds Rust-style raw strings (docs/LANGUAGE_SPEC.md section 15):
//   * the RAW_STRING_LITERAL lexer rule matches the contiguous opening delimiter 'r' + N '#' + '"'
//     and its action scans the counted body, because ANTLR cannot express an arbitrary counted '#'
//     delimiter directly;
//   * the body closes on the first '"' followed by exactly N '#' characters, so the whole literal
//     is one token and its internal physical newlines can never participate in semicolon insertion;
//   * an unterminated raw string consumes to end of input and reports a lexical diagnostic at the
//     opening delimiter naming the expected closing delimiter.
//
// Phase 4 adds the remaining statement forms of the static core: assignment statements, `while`,
// three-clause `for`, `break`, and `continue` (docs/LANGUAGE_SPEC.md sections 2, 3, 17). It also
// adds the operator set that the static type checker types: unary `-` and `!`, ordering
// comparisons, equality, and short-circuit `&&`/`||` (docs/LANGUAGE_SPEC.md section 3). Operators
// that depend on later phases stay absent: `??`, `?.`, `is`, and `as` (nullability/type tests) are
// not lexed as operators here.
//   * assignment is a statement, never an expression: `exprStmt` accepts one optional `=`, and the
//     `for` clause rules accept the same shape. The static semantic pass rejects an assignment
//     whose target is not a mutable local.
//   * `BREAK` and `CONTINUE` are terminators for semicolon insertion; that table lives in
//     org.solvik.parser.SemicolonInsertingTokenSource.
//
// Phase 6 adds classes and objects (docs/LANGUAGE_SPEC.md section 7): a class declaration with
// property declarations, at most one constructor, and instance methods, plus the `this` expression.
//   * `classDecl` joins `functionDecl` at the top level; class members are `propertyDecl`,
//     `constructorDecl`, or a method declaration. A class body tolerates
//     stand-alone SEMI tokens because a method/constructor body ends in `}`, which is itself a
//     semicolon-insertion terminator, so a synthetic `;` may follow it before the class's `}`.
//   * `thisExpr` joins `primary`, so `this`, `this.name`, and `this.method(...)` parse through the
//     ordinary postfix suffix machinery (member access and calls).
//   * property declarations require an explicit type annotation, matching the specification's rule
//     that only local variables may infer a type from an initializer.
//   * `this` is a value-producing atom; THIS joins the semicolon-insertion terminator table so a
//     statement ending in `this` terminates like one ending in an identifier or literal.
//
// Phase 7 adds the root type hierarchy and single inheritance (docs/LANGUAGE_SPEC.md sections 4
// and 7):
//   * `classDecl` accepts an optional leading `open` and an optional `extends typeRef`; the
//     grammar itself already enforces at most one superclass, so multiple inheritance is a parse
//     error.
//   * `methodDecl` is a class-only method declaration with optional `open`/`override` modifiers;
//     a top-level `functionDecl` accepts no modifiers because top-level functions are never
//     overridable.
//   * `superExpr` joins `primary` so `super(arguments)` and `super.member(...)` parse through the
//     ordinary call/member suffix machinery; the semantic layer restricts where they may appear.
//   * `LONG_LITERAL`, `FLOATING_LITERAL`, and `CHARACTER_LITERAL` join the literal forms. `Byte` and
//     `Short` values have no literal form and are produced by explicit conversions.
// Phase 8 adds interfaces and default methods (docs/LANGUAGE_SPEC.md section 8):
//   * `interfaceDecl` joins `classDecl` at the top level. An interface may `extends` a
//     comma-separated list of interfaces, so interface extension is multiple while class
//     inheritance stays single.
//   * an interface body holds only `signatureDecl` (an abstract signature terminated by `;`) and
//     `defaultMethodDecl` (a `func` with a body). Interfaces contain methods, not stored
//     properties, so `propertyDecl` and `constructorDecl` are absent from `interfaceMember` and are
//     rejected by the parser inside an interface body.
//   * `classDecl` accepts `implements typeRef, ...` (after its optional `extends`), which is the
//     multiple-interface surface of nominal conformance. `implements` is not part of `interfaceDecl`
//     because an interface extends interfaces; it never implements them.
//   * interface members carry no `open`/`override` modifiers: an interface method is inherited by
//     every implementor, and a class implementing method needs no modifier.
//
// Phase 9 adds composition through delegation (docs/LANGUAGE_SPEC.md section 9):
//   * `delegateDecl` joins `classMember` as `delegate val name: InterfaceType;`. A delegate is a
//     property declaration whose type annotation is required (there is no inference), and which may
//     carry a declaration initializer like any other property: only a `val` may be a delegate and it
//     is initialized under the normal constructor rules.
//   * a delegate's declared type must be an interface, because delegation forwards interface
//     members; the semantic layer, not the grammar, decides which members a delegate supplies and
//     rejects a non-interface, class-typed, or ambiguous delegate.
//   * `delegate` is not a semicolon-insertion terminator (like `class`, `implements`, and `var`):
//     a delegate declaration always ends in `;`, which is the token that terminates it.
// Phase 11 adds nominal generics (docs/LANGUAGE_SPEC.md section 11):
//   * `classDecl`, `interfaceDecl`, `functionDecl`, `methodDecl`, `signatureDecl`, and
//     `defaultMethodDecl` accept a `typeParameterList` after the declared name, so a generic
//     declaration carries its own type parameters;
//   * `typeRef` accepts an optional `typeArguments` list, so a written type may be a generic type
//     application such as `List<String>` or `Box<User>`. A bare name of a generic declaration is
//     syntactically valid and is rejected by the semantic layer as a raw generic type.
//   * A call may spell its type arguments with `Name<T>(...)` immediately before the argument list,
//     so a generic construction, function, or method call can bind its type parameters explicitly
//     instead of inferring them. The type arguments sit inside the call suffix that requires `(`,
//     which keeps the `LT`/`GT` tokens unambiguous with relational operators.
//   * Call sites that omit explicit type arguments still infer them from value arguments.
//   * Enums, regex, and switch remain absent and are rejected by the parser.
//
// Phase 12 adds enums and sealed types (docs/LANGUAGE_SPEC.md section 12):
//   * `enumDecl` joins the top-level declarations: `enum Name<T, ...> { Variant(Type, ...) ... }`.
//     Each `enumVariant` is a positional, value-carrying nested constructor name terminated by a
//     real or inserted SEMI, exactly like an interface signature. A variant with no values omits the
//     parentheses.
//   * `classDecl` accepts an optional leading `sealed` modifier. A sealed class is abstract (never
//     constructible) and is the only declaration kind besides `open` that a subclass may extend,
//     because its complete same-file subtype set is closed for exhaustiveness analysis.
//   * `enum`, `sealed`, and a variant name are not semicolon-insertion terminators: `enum` and
//     `sealed` open a construct, and a variant already ends in `)` or an identifier, both of which
//     are terminators already.
//
// Phase 10 adds null safety (docs/LANGUAGE_SPEC.md sections 5 and 18):
//   * `typeRef` accepts an optional `?`, so `T?` is a written nullable type. The type name resolves
//     as before and the semantic layer wraps the resolved type in `NullableType`, which keeps
//     identity comparison reliable because the view is canonical per type instance.
//   * `null` joins the literal forms as a keyword. It is a value-producing atom, so NULL joins the
//     semicolon-insertion terminator table.
//   * `?.` is now a member-suffix operator as well as an insertion lookahead token; a nullable
//     receiver may only be dereferenced through `?.` or after a null check.
//   * `??` is the lowest-precedence binary operator, below `||`, and is lexed as one token so it can
//     never be confused with a nullable type reference.
//   * `is` and `as` join the ordering tier as keywords whose right operand is a type reference, not an
//     expression. They are non-terminators for semicolon insertion, but QUESTION joins the terminator
//     table because a newline after `T?` must terminate the statement the type reference belongs to.
//
// Phase 13 adds exhaustive `match` (docs/LANGUAGE_SPEC.md section 12):
//   * `matchExpr` joins `primary` as an expression: `match <expression> { <branch>* }`. A branch is
//     `pattern => expression`; its result expression is a value-producing expression. Newlines
//     after a branch result already insert a SEMI because a result ends in an identifier, literal,
//     or `)`, so the branch list tolerates stand-alone SEMI tokens exactly like a block.
//   * `pattern` is `Identifier (':' typeRef | '(' patternList? ')')?`. The semantic layer interprets
//     a colon form as a sealed-subtype binding, a parenthesized form as an enum variant pattern,
//     and a bare name as a value-less variant at the top level or a binding inside a variant. The
//     wildcard `_` is the bare name `_`, matching the specification's identifier grammar rather
//     than reserving a new keyword.
//   * `ARROW` and `MATCH` are new tokens; neither is a semicolon-insertion terminator (`match`
//     opens a construct and `=>` is always followed by the branch expression).
//
// Phase 15 adds the non-fallthrough `switch` statement (docs/LANGUAGE_SPEC.md section 13):
//   * `switchStmt` joins `statement`: `switch (value) { cases }`. A case body is an implicit block,
//     written as a `(statement | SEMI)*` sequence that ends where the next `case`, `default`, or
//     the switch's closing `}` begins, because neither `case` nor `default` can start a statement.
//   * a non-default case carries one or more comma-separated labels (`case 1, 2:`), and `default`
//     is its own alternative so the semantic layer can enforce "at most one and last".
//   * `regexCaseLabel` is `regex <string literal>`: the reserved `regex` keyword plus a normal or
//     raw string pattern, matching the specification's `case regex r#"..."#` surface.
//   * `SWITCH`, `CASE`, `DEFAULT`, and `REGEX_KW` are new tokens. `switch` and `case` open a
//     construct; `default` and `regex` are followed by `:` and a string literal respectively, so
//     none is a semicolon-insertion terminator.
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
     * literal is consumed as one token, its embedded newlines never reach the semicolon-inserting
     * token stream.
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
// top-level `val`/`var` is therefore a local of the implicit main, not a global.
// Phase 16 adds compile-time `include` (docs/LANGUAGE_SPEC.md section 20): a top-level-only
// directive `include <string literal>` whose target file is parsed and spliced into the program
// before semantic analysis. `include` is reserved so it can no longer be an identifier.
compilationUnit: moduleDecl? (includeDecl | functionDecl | classDecl | interfaceDecl | enumDecl | statement | SEMI)* EOF ;

// Phase 17: an optional module declaration naming the file's namespace. It must be the first item in
// a physical file and is terminated by a real or lexically inserted SEMI. The written name is a
// single identifier with no dots; the semantic layer enforces the lowercase, underscore-separated
// naming rule and rejects a reserved word.
moduleDecl: MODULE Identifier SEMI ;

// A compile-time include directive. It takes a normal or raw string path and is terminated by a real
// or lexically inserted SEMI. It is not a statement and may only appear at the top level. Phase 17
// adds an optional `alias <name>` suffix that binds a file-local prefix to the included file's
// module instead of splicing its declarations into the flat program scope.
includeDecl: INCLUDE (stringLiteral | rawStringLiteral) (ALIAS Identifier)? SEMI ;

// A callable's return type is optional (docs/LANGUAGE_SPEC.md section 6): a declaration that
// returns a value writes `: Type`, while a declaration that returns no value omits it and is
// typed `Unit`. The AST builder synthesizes the omitted `Unit` reference, so the parser and the
// semantic layer see a return type on every callable and need no special case. Parameter types
// remain mandatory.
functionDecl: FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? block ;

classDecl: SEALED? OPEN? CLASS Identifier typeParameterList? (EXTENDS typeRef)? (IMPLEMENTS typeRefList)? LBRACE (classMember | SEMI)* RBRACE ;

interfaceDecl: INTERFACE Identifier typeParameterList? (EXTENDS typeRefList)? LBRACE (interfaceMember | SEMI)* RBRACE ;

// An enum declaration (docs/LANGUAGE_SPEC.md section 12). Variants are nested nominal
// constructors that may carry positional values; every variant is terminated by a real or inserted
// SEMI, because the grammar tolerates a variant list spread across physical lines.
enumDecl: ENUM Identifier typeParameterList? LBRACE (enumVariant | SEMI)* RBRACE ;

enumVariant: Identifier (LPAREN typeRefList? RPAREN)? SEMI ;

interfaceMember: signatureDecl | defaultMethodDecl ;

// An abstract interface signature: no body, terminated by `;`, which is a real SEMI token, so a
// default-method body's `}` and this `;` are the two interface-member terminators. Like every
// callable, its return type is optional and defaults to `Unit`.
signatureDecl: FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? SEMI ;

defaultMethodDecl: FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? block ;

typeRefList: typeRef (COMMA typeRef)* ;

classMember: propertyDecl | delegateDecl | constructorDecl | methodDecl ;

// A delegate (docs/LANGUAGE_SPEC.md section 9): an immutable, explicitly typed property that the
// compiler forwards unresolved interface members to. The type annotation is required and must name
// an interface; the optional initializer is permitted because a delegate is initialized under the
// normal constructor rules, exactly like any other property.
delegateDecl: DELEGATE VAL Identifier COLON typeRef (ASSIGN expression)? SEMI ;

methodDecl: methodModifier* FUNC Identifier typeParameterList? LPAREN parameterList? RPAREN (COLON typeRef)? block ;

methodModifier: OPEN | OVERRIDE ;

propertyDecl: bindingKind Identifier COLON typeRef (ASSIGN expression)? SEMI ;

// A constructor (docs/LANGUAGE_SPEC.md section 7): a class member named after the enclosing class
// with no `func` keyword and no return type. Calling the class name invokes it. The grammar accepts
// any identifier here; the semantic layer requires it to match the enclosing class name, reports a
// declaration whose name does not match, and rejects a non-constructor member named after the class.
constructorDecl: Identifier LPAREN parameterList? RPAREN block ;

parameterList: parameter (COMMA parameter)* ;

parameter: Identifier COLON typeRef ;

// A generic declaration's type parameter list, e.g. `<T>` or `<K, V>`. Bounds are not part of the
// initial language, so each parameter is a bare name.
typeParameterList: LT Identifier (COMMA Identifier)* GT ;

// A written type: an optional module prefix, a name, optional type arguments, and an optional
// nullable marker. The prefix is a single identifier separated by `::` (Phase 17); `::` is
// deliberately distinct from `.` member access so a module-qualified type can never be confused
// with a member access. The semantic layer decides whether a bare generic name is a raw-type error
// or a declared type parameter.
typeRef: Identifier (COLONCOLON Identifier)? typeArguments? QUESTION? ;

typeArguments: LT typeRef (COMMA typeRef)* GT ;

block: LBRACE (statement | SEMI)* RBRACE ;

statement: localDecl | ifStmt | whileStmt | forStmt | forInStmt | switchStmt | block | breakStmt | continueStmt | returnStmt | exprStmt ;

localDecl: bindingKind Identifier (COLON typeRef)? ASSIGN expression SEMI ;

bindingKind: VAL | VAR ;

ifStmt: IF LPAREN expression RPAREN block elseBranch? ;

elseBranch: ELSE ifStmt | ELSE block ;

whileStmt: WHILE LPAREN expression RPAREN block ;

forStmt: FOR LPAREN forInit? SEMI forCondition? SEMI forUpdate? RPAREN block ;

// A range for-in loop: `for (name in start <op> end) block`. The three range operators are
// distinct tokens, so `..` remains string concatenation outside a for-in header. The loop variable
// is implicitly declared by the semantic layer; bounds are Integer expressions evaluated once.
forInStmt: FOR LPAREN Identifier IN rangeExpr RPAREN block ;

rangeExpr: expression rangeOperator expression ;

rangeOperator: DOTDOTDOT | DOTDOTLT | DOTDOTGT ;

forInit: localDeclNoSemi | assignable ;

forCondition: expression ;

forUpdate: assignable ;

// Phase 15: `switch` is a statement for value dispatch with no implicit fallthrough. A case body is
// an implicit block: a sequence of statements that ends where the next `case`, `default`, or the
// switch's closing `}` begins, because those keywords cannot start a statement. `default` is a
// separate alternative so the semantic layer can enforce at most one and last. A `regex` case label
// carries a normal or raw string literal pattern.
switchStmt: SWITCH LPAREN expression RPAREN LBRACE (switchCase | defaultCase | SEMI)* RBRACE ;

// Phase 18: block, `if`, and `switch` expressions (docs/LANGUAGE_SPEC.md section 21). The
// surface syntax is shared with the statement forms; the syntactic context selects the expression
// node. A value-required block accepts an optional unterminated terminal expression (`valueTail`)
// so `{ 42 }` and `{ 42; }` and a newline-inserted equivalent all parse to the same result. The
// AST builder still identifies the tail structurally, so explicit and synthesized semicolons are
// interchangeable. An expression `if` retains an optional `else` so a missing `else` can be
// reported with a dedicated diagnostic.
blockExpr: valueBlock ;

ifExpr: IF LPAREN expression RPAREN valueBlock elseExprBranch? ;

elseExprBranch: ELSE ifExpr | ELSE valueBlock ;

switchExpr: SWITCH LPAREN expression RPAREN LBRACE (valueSwitchCase | valueDefaultCase | SEMI)* RBRACE ;

// A value-required braced body: ordinary terminated statements followed by an optional
// unterminated terminal expression. The terminal expression is the block or case result.
valueBlock: LBRACE (statement | SEMI)* valueTail? RBRACE ;

valueTail: expression ;

valueSwitchCase: CASE caseLabel (COMMA caseLabel)* COLON valueCaseBody ;

valueDefaultCase: DEFAULT COLON valueCaseBody ;

valueCaseBody: (statement | SEMI)* valueTail? ;

switchCase: CASE caseLabel (COMMA caseLabel)* COLON (statement | SEMI)* ;

defaultCase: DEFAULT COLON (statement | SEMI)* ;

caseLabel: regexCaseLabel | expression ;

regexCaseLabel: REGEX_KW (stringLiteral | rawStringLiteral) ;

localDeclNoSemi: bindingKind Identifier (COLON typeRef)? ASSIGN expression ;

assignable: expression (ASSIGN expression)? ;

breakStmt: BREAK SEMI ;

continueStmt: CONTINUE SEMI ;

returnStmt: RETURN expression? SEMI ;

exprStmt: expression (ASSIGN expression)? SEMI ;

expression: nullCoalescing ;

// Phase 10: `??` is the lowest-precedence binary operator (docs/LANGUAGE_SPEC.md section 3).
nullCoalescing: logicalOr (NULL_COALESCE logicalOr)* ;

logicalOr: logicalAnd (OR logicalAnd)* ;

logicalAnd: equality (AND equality)* ;

equality: relational ((EQ | NEQ | EQEQ | NEQEQ) relational)* ;

// A binary expression tier for string concatenation `..`, which binds looser than arithmetic but
// tighter than comparison (docs/LANGUAGE_SPEC.md section 3). Each operand is rendered through
// `toString`.
relational: concat relation* ;

// Phase 10: `is` and `as` share the ordering tier, but their right operand is a written type rather
// than an expression. A separate `relation` alternative keeps the left-associative fold explicit.
relation: (LT | LE | GT | GE) concat | IS typeRef | AS typeRef ;

concat: additive (DOTDOT additive)* ;

additive: multiplicative ((ADD | SUB) multiplicative)* ;

multiplicative: unary ((MUL | DIV) unary)* ;

unary: (BANG | SUB) unary | postfix ;

postfix: primary suffix* ;

primary: literal | paren | thisExpr | superExpr | matchExpr | ifExpr | switchExpr | blockExpr | name ;

// Phase 13: `match` is expression-oriented and exhaustive for a known closed variant set. A branch
// result is terminated by a real or inserted SEMI, which the enclosing branch list consumes.
matchExpr: MATCH expression LBRACE (matchBranch | SEMI)* RBRACE ;

matchBranch: pattern ARROW expression ;

pattern: Identifier (COLON typeRef | LPAREN patternList? RPAREN)? ;

patternList: pattern (COMMA pattern)* ;

paren: LPAREN expression RPAREN ;

thisExpr: THIS ;

superExpr: SUPER ;

name: Identifier ;

suffix: memberSuffix | namespaceSuffix | callSuffix ;

memberSuffix: (DOT | NULLABLE_DOT) Identifier ;

// A module-qualified name `prefix::name`. `::` is the namespace separator and is distinct from the
// `.` member-access operator, so a qualified reference is unambiguous without name-based
// heuristics. It chains, so `math::Result::Ok` is a valid path.
namespaceSuffix: COLONCOLON Identifier ;

callSuffix: typeArguments? LPAREN argumentList? RPAREN ;

// A call argument. A `key: value` entry names a key/value pair and is meaningful only in a
// built-in `Map` construction; the semantic pass rejects an entry in any other argument list.
// A trailing comma is permitted after the last argument (for example a multiline argument list
// that ends in `,\n)`), and it contributes no argument. A list still requires at least one
// argument, so `f(,)` is a parse error; `f()` takes the empty path through `callSuffix` instead.
argumentList: callArgument (COMMA callArgument)* COMMA? ;
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

FUNC: 'func' ;
// Phase 16: `include` introduces a compile-time file inclusion; reserved so it cannot be an identifier.
INCLUDE: 'include' ;
// Phase 17: `module` names a file's namespace and `alias` binds a file-local include prefix. Both are
// reserved so they cannot be identifiers. Neither is a semicolon-insertion terminator: a module
// declaration ends in its name and an include alias ends in its alias name, both identifiers.
MODULE: 'module' ;
ALIAS: 'alias' ;
CLASS: 'class' ;
INTERFACE: 'interface' ;
// Phase 12: `enum` introduces a closed set of value-carrying variants, and `sealed` marks a class
// whose same-file subtype set is complete for exhaustiveness analysis.
ENUM: 'enum' ;
SEALED: 'sealed' ;
// Phase 9: `delegate` introduces a forwarding property; it is reserved so it cannot be an identifier.
DELEGATE: 'delegate' ;
IMPLEMENTS: 'implements' ;
OPEN: 'open' ;
EXTENDS: 'extends' ;
OVERRIDE: 'override' ;
THIS: 'this' ;
SUPER: 'super' ;
VAL: 'val' ;
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
// Phase 13: `match` introduces the exhaustive match expression and `=>` separates a branch's
// pattern from its result.
MATCH: 'match' ;
ARROW: '=>' ;
// Phase 15: the non-fallthrough `switch` statement. `switch` and `case` open a construct, and
// `default`/`regex` are followed by `:` and a string literal respectively, so none terminates a line
// for semicolon insertion.
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
// Lexed for semicolon-insertion lookahead only (Phase 2): its presence must suppress insertion.
// Phase 10 makes it the safe member-access operator as well.
NULLABLE_DOT: '?.' ;
// Phase 10: `??` is null coalescing; it is lexed as one token so it is never read as `T?` plus `?`.
NULL_COALESCE: '??' ;
// Phase 10: `?` marks a nullable type reference.
QUESTION: '?' ;
// Lexed so insertion can track unmatched '[' depth (specification condition 1). Bracket syntax
// itself arrives in a later phase, so no grammar rule consumes these tokens yet.
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
// complete literal is emitted as one token; embedded newlines therefore never reach the
// semicolon-inserting token stream.
RAW_STRING_LITERAL: 'r' '#'* '"' { lexRawStringBody(); } ;
