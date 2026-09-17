// Solvik front-end grammar.
// Generated sources are produced by generate_parser.sh; do not edit generated files by hand.
//
// Phase 1 supported constructs: top-level `fun` declarations with typed parameters and explicit
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
// property declarations, at most one `init`, and instance methods, plus the `this` expression.
//   * `classDecl` joins `functionDecl` at the top level; class members are `propertyDecl`,
//     `initDecl`, or a method declaration. A class body tolerates stand-alone SEMI tokens because
//     a method/init body ends in `}`, which is itself a semicolon-insertion terminator, so a
//     synthetic `;` may follow it before the class's `}`.
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
//   * `LONG_LITERAL`, `FLOATING_LITERAL`, and `CHAR_LITERAL` join the literal forms. `Byte` and
//     `Short` values have no literal form and are produced by explicit conversions.
// Phase 8 adds interfaces and default methods (docs/LANGUAGE_SPEC.md section 8):
//   * `interfaceDecl` joins `classDecl` at the top level. An interface may `extends` a
//     comma-separated list of interfaces, so interface extension is multiple while class
//     inheritance stays single.
//   * an interface body holds only `signatureDecl` (an abstract signature terminated by `;`) and
//     `defaultMethodDecl` (a `fun` with a body). Interfaces contain methods, not stored
//     properties, so `propertyDecl` and `initDecl` are absent from `interfaceMember` and are
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
//   * Type-argument syntax deliberately uses the same `LT`/`GT` tokens as relational operators; it
//     is unambiguous because a `typeRef` only appears in a type position. Call sites never spell
//     type arguments: generic construction, function, and method calls infer them from arguments.
//   * Enums, regex, and switch remain absent and are rejected by the parser.
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

compilationUnit: (functionDecl | classDecl | interfaceDecl | SEMI)* EOF ;

functionDecl: FUN Identifier typeParameterList? LPAREN parameterList? RPAREN COLON typeRef block ;

classDecl: OPEN? CLASS Identifier typeParameterList? (EXTENDS typeRef)? (IMPLEMENTS typeRefList)? LBRACE (classMember | SEMI)* RBRACE ;

interfaceDecl: INTERFACE Identifier typeParameterList? (EXTENDS typeRefList)? LBRACE (interfaceMember | SEMI)* RBRACE ;

interfaceMember: signatureDecl | defaultMethodDecl ;

// An abstract interface signature: no body, terminated by `;`, which is a real SEMI token, so a
// default-method body's `}` and this `;` are the two interface-member terminators.
signatureDecl: FUN Identifier typeParameterList? LPAREN parameterList? RPAREN COLON typeRef SEMI ;

defaultMethodDecl: FUN Identifier typeParameterList? LPAREN parameterList? RPAREN COLON typeRef block ;

typeRefList: typeRef (COMMA typeRef)* ;

classMember: propertyDecl | delegateDecl | initDecl | methodDecl ;

// A delegate (docs/LANGUAGE_SPEC.md section 9): an immutable, explicitly typed property that the
// compiler forwards unresolved interface members to. The type annotation is required and must name
// an interface; the optional initializer is permitted because a delegate is initialized under the
// normal constructor rules, exactly like any other property.
delegateDecl: DELEGATE VAL Identifier COLON typeRef (ASSIGN expression)? SEMI ;

methodDecl: methodModifier* FUN Identifier typeParameterList? LPAREN parameterList? RPAREN COLON typeRef block ;

methodModifier: OPEN | OVERRIDE ;

propertyDecl: bindingKind Identifier COLON typeRef (ASSIGN expression)? SEMI ;

initDecl: INIT LPAREN parameterList? RPAREN block ;

parameterList: parameter (COMMA parameter)* ;

parameter: Identifier COLON typeRef ;

// A generic declaration's type parameter list, e.g. `<T>` or `<K, V>`. Bounds are not part of the
// initial language, so each parameter is a bare name.
typeParameterList: LT Identifier (COMMA Identifier)* GT ;

// A written type: a name, optional type arguments, and an optional nullable marker. The semantic
// layer decides whether a bare generic name is a raw-type error or a declared type parameter.
typeRef: Identifier typeArguments? QUESTION? ;

typeArguments: LT typeRef (COMMA typeRef)* GT ;

block: LBRACE (statement | SEMI)* RBRACE ;

statement: localDecl | ifStmt | whileStmt | forStmt | breakStmt | continueStmt | returnStmt | exprStmt ;

localDecl: bindingKind Identifier (COLON typeRef)? ASSIGN expression SEMI ;

bindingKind: VAL | VAR ;

ifStmt: IF LPAREN expression RPAREN block elseBranch? ;

elseBranch: ELSE ifStmt | ELSE block ;

whileStmt: WHILE LPAREN expression RPAREN block ;

forStmt: FOR LPAREN forInit? SEMI forCondition? SEMI forUpdate? RPAREN block ;

forInit: localDeclNoSemi | assignable ;

forCondition: expression ;

forUpdate: assignable ;

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

equality: relational ((EQ | NEQ) relational)* ;

// Phase 10: `is` and `as` share the ordering tier, but their right operand is a written type rather
// than an expression. A separate `relation` alternative keeps the left-associative fold explicit.
relational: additive relation* ;

relation: (LT | LE | GT | GE) additive | IS typeRef | AS typeRef ;

additive: multiplicative ((ADD | SUB) multiplicative)* ;

multiplicative: unary ((MUL | DIV) unary)* ;

unary: (BANG | SUB) unary | postfix ;

postfix: primary suffix* ;

primary: literal | paren | thisExpr | superExpr | name ;

paren: LPAREN expression RPAREN ;

thisExpr: THIS ;

superExpr: SUPER ;

name: Identifier ;

suffix: memberSuffix | callSuffix ;

memberSuffix: (DOT | NULLABLE_DOT) Identifier ;

callSuffix: LPAREN argumentList? RPAREN ;

argumentList: expression (COMMA expression)* ;

literal: intLiteral | longLiteral | floatingLiteral | boolLiteral | charLiteral | stringLiteral | rawStringLiteral | nullLiteral ;

intLiteral: INT_LITERAL ;

longLiteral: LONG_LITERAL ;

floatingLiteral: FLOATING_LITERAL ;

boolLiteral: BOOL_LITERAL ;

charLiteral: CHAR_LITERAL ;

stringLiteral: STRING_LITERAL ;

rawStringLiteral: RAW_STRING_LITERAL ;

// Phase 10: `null` is a keyword literal, not an identifier.
nullLiteral: NULL ;

FUN: 'fun' ;
CLASS: 'class' ;
INTERFACE: 'interface' ;
// Phase 9: `delegate` introduces a forwarding property; it is reserved so it cannot be an identifier.
DELEGATE: 'delegate' ;
IMPLEMENTS: 'implements' ;
OPEN: 'open' ;
EXTENDS: 'extends' ;
OVERRIDE: 'override' ;
INIT: 'init' ;
THIS: 'this' ;
SUPER: 'super' ;
VAL: 'val' ;
VAR: 'var' ;
IF: 'if' ;
ELSE: 'else' ;
WHILE: 'while' ;
FOR: 'for' ;
BREAK: 'break' ;
CONTINUE: 'continue' ;
RETURN: 'return' ;
// Phase 10 null-safety keywords.
NULL: 'null' ;
IS: 'is' ;
AS: 'as' ;
BOOL_LITERAL: 'true' | 'false' ;
Identifier: [a-zA-Z_][a-zA-Z0-9_]* ;
INT_LITERAL: [0-9]+ ;
// Phase 7: `L`/`l` suffix selects Long; longest-match keeps `123L` distinct from `123`.
LONG_LITERAL: [0-9]+ [Ll] ;
// Phase 7: decimal floating-point literals with an optional exponent. A trailing `f`/`F` selects
// Float; otherwise the literal has type Double.
FLOATING_LITERAL: [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)? [fF]?
               | [0-9]+ [eE] [+-]? [0-9]+ [fF]?
               ;
// Phase 7: a character literal contains exactly one Unicode scalar value or one escape; the
// semantic layer validates the escape set and reports an empty or multi-character literal.
CHAR_LITERAL: '\'' (~['\\\r\n] | '\\' .) '\'' ;
STRING_LITERAL: '"' (~["\\\r\n] | '\\' .)* '"' ;
LPAREN: '(' ;
RPAREN: ')' ;
LBRACE: '{' ;
RBRACE: '}' ;
SEMI: ';' ;
ASSIGN: '=' ;
COLON: ':' ;
COMMA: ',' ;
DOT: '.' ;
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
