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
// Interfaces, delegation, nullability, and regex remain absent and are rejected by the parser.
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

compilationUnit: (functionDecl | classDecl | SEMI)* EOF ;

functionDecl: FUN Identifier LPAREN parameterList? RPAREN COLON typeRef block ;

classDecl: OPEN? CLASS Identifier (EXTENDS typeRef)? LBRACE (classMember | SEMI)* RBRACE ;

classMember: propertyDecl | initDecl | methodDecl ;

methodDecl: methodModifier* FUN Identifier LPAREN parameterList? RPAREN COLON typeRef block ;

methodModifier: OPEN | OVERRIDE ;

propertyDecl: bindingKind Identifier COLON typeRef (ASSIGN expression)? SEMI ;

initDecl: INIT LPAREN parameterList? RPAREN block ;

parameterList: parameter (COMMA parameter)* ;

parameter: Identifier COLON typeRef ;

typeRef: Identifier ;

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

expression: logicalOr ;

logicalOr: logicalAnd (OR logicalAnd)* ;

logicalAnd: equality (AND equality)* ;

equality: relational ((EQ | NEQ) relational)* ;

relational: additive ((LT | LE | GT | GE) additive)* ;

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

memberSuffix: DOT Identifier ;

callSuffix: LPAREN argumentList? RPAREN ;

argumentList: expression (COMMA expression)* ;

literal: intLiteral | longLiteral | floatingLiteral | boolLiteral | charLiteral | stringLiteral | rawStringLiteral ;

intLiteral: INT_LITERAL ;

longLiteral: LONG_LITERAL ;

floatingLiteral: FLOATING_LITERAL ;

boolLiteral: BOOL_LITERAL ;

charLiteral: CHAR_LITERAL ;

stringLiteral: STRING_LITERAL ;

rawStringLiteral: RAW_STRING_LITERAL ;

FUN: 'fun' ;
CLASS: 'class' ;
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
// No grammar rule consumes it until nullable member access lands in Phase 10, so a program using
// '?.' is lexed and then rejected by the parser with an ordinary unexpected-token error.
NULLABLE_DOT: '?.' ;
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
