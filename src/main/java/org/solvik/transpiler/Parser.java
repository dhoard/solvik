package org.solvik.transpiler;

import java.util.ArrayList;
import java.util.List;

import static org.solvik.transpiler.Ast.*;
import static org.solvik.transpiler.language.Language.*;

/** Recursive-descent declaration parser with precedence-climbing expressions. */
public final class Parser {
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int pos;

    public Parser(List<Token> tokens) { this.tokens = tokens; }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }

    public CompilationUnit parse(String file) {
        skipLines();
        Token start = peek();
        String packageName = "";
        if (take(TokenKind.PACKAGE)) {
            packageName = dottedName("package name");
            terminator();
        } else error("P001", "a package declaration is required", peek());
        List<UseDecl> uses = new ArrayList<>();
        while (take(TokenKind.USE)) {
            Token use = previous();
            String scheme = expect(TokenKind.IDENT, "'file' or 'url'").text();
            expect(TokenKind.COLON, "':' after use scheme");
            String path = dottedName("dependency path");
            String alias = null;
            if (isIdent("as")) { advance(); alias = expect(TokenKind.IDENT, "use alias").text(); }
            terminator();
            uses.add(new UseDecl(scheme, path, alias, spanFrom(use, previous())));
        }
        List<Decl> declarations = new ArrayList<>();
        while (!check(TokenKind.EOF)) {
            skipLines();
            if (check(TokenKind.EOF)) break;
            if (check(TokenKind.STRUCT)) declarations.add(parseStruct());
            else if (check(TokenKind.TRAIT)) declarations.add(parseTrait());
            else if (check(TokenKind.ENUM)) declarations.add(parseEnum());
            else { error("P001", "expected struct, trait, or enum declaration", peek()); recoverTopLevel(); }
        }
        return new CompilationUnit(packageName, List.copyOf(uses), List.copyOf(declarations), spanFrom(start, previous()));
    }

    private StructDecl parseStruct() {
        Token start = advance();
        Token name = expect(TokenKind.IDENT, "struct name");
        requireCase(name, true, "struct names");
        List<TypeParam> params = typeParams();
        if (take(TokenKind.EXTENDS)) { error("P001", "struct inheritance is not supported", previous()); recoverTopLevel(); }
        List<TypeRef> implementsTypes = new ArrayList<>();
        if (take(TokenKind.IMPLEMENTS)) {
            implementsTypes.add(typeRef());
            while (take(TokenKind.COMMA)) implementsTypes.add(typeRef());
        }
        skipLines(); expect(TokenKind.LBRACE, "'{' opening struct body");
        List<FieldDecl> fields = new ArrayList<>(); List<MethodDecl> methods = new ArrayList<>();
        List<DelegateDecl> delegates = new ArrayList<>(); Block staticBlock = null;
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            skipLines();
            if (check(TokenKind.RBRACE)) break;
            if (take(TokenKind.DELEGATE)) {
                Token ds = previous(); TypeRef it = typeRef(); expect(TokenKind.TO, "'to' after delegated trait");
                Token f = expect(TokenKind.IDENT, "delegate target field"); terminator();
                delegates.add(new DelegateDecl(it, f.text(), spanFrom(ds, previous()))); continue;
            }
            boolean isPub = take(TokenKind.PUB);
            boolean isStatic = take(TokenKind.STATIC);
            if (isStatic && (check(TokenKind.LBRACE) || (check(TokenKind.NEWLINE) && nextNonLine(TokenKind.LBRACE)))) {
                skipLines();
                if (staticBlock != null) error("P001", "a struct may declare at most one static block", peek());
                staticBlock = block(); continue;
            }
            boolean isVar = take(TokenKind.VAR);
            if (take(TokenKind.FUNC)) {
                if (isStatic) error("P001", "static is not a method modifier", previous());
                pos--;
                methods.add(method(isPub, false)); continue;
            }
            if (isStatic && !check(TokenKind.IDENT)) error("P001", "static must introduce a field", previous());
            Token member = expect(TokenKind.IDENT, "field name");
            if (!take(TokenKind.COLON)) {
                error("P001", "method declarations require the 'func' keyword", member);
                recoverMember(); continue;
            }
            TypeRef type = typeRef(); Expr init = null;
            if (isStatic) { expect(TokenKind.ASSIGN, "'=' after static field type"); init = expression(); }
            else if (isPub) error("P001", "fields are always private", member);
            terminator();
            fields.add(new FieldDecl(member.text(), type, isVar, isStatic, init, member.span()));
        }
        expect(TokenKind.RBRACE, "'}' closing struct body");
        return new StructDecl(name.text(), params, implementsTypes, delegates, fields, methods, staticBlock, spanFrom(start, previous()));
    }

    private TraitDecl parseTrait() {
        Token start = advance(); Token name = expect(TokenKind.IDENT, "trait name"); requireCase(name, true, "trait names");
        List<TypeParam> params = typeParams(); List<TypeRef> parents = new ArrayList<>();
        if (take(TokenKind.EXTENDS)) { parents.add(typeRef()); while (take(TokenKind.COMMA)) parents.add(typeRef()); }
        skipLines(); expect(TokenKind.LBRACE, "'{' opening trait body"); List<MethodDecl> methods = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) { skipLines(); if (check(TokenKind.RBRACE)) break; methods.add(method(true, true)); }
        expect(TokenKind.RBRACE, "'}' closing trait body");
        return new TraitDecl(name.text(), params, parents, methods, spanFrom(start, previous()));
    }

    private EnumDecl parseEnum() {
        Token start = advance(); Token name = expect(TokenKind.IDENT, "enum name"); requireCase(name, true, "enum names");
        List<TypeParam> params = typeParams(); skipLines(); expect(TokenKind.LBRACE, "'{' opening enum body"); List<VariantDecl> variants = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            skipLines(); if (check(TokenKind.RBRACE)) break;
            Token v = expect(TokenKind.IDENT, "enum variant name"); requireCase(v, false, "enum variant names");
            TypeRef payload = null;
            if (take(TokenKind.LPAREN)) { payload = typeRef(); expect(TokenKind.RPAREN, "')' after variant payload"); }
            if (take(TokenKind.COMMA)) error("P001", "enum variants must be separated by newlines", previous()); else terminator();
            variants.add(new VariantDecl(v.text(), payload, v.span()));
        }
        expect(TokenKind.RBRACE, "'}' closing enum body");
        return new EnumDecl(name.text(), params, variants, spanFrom(start, previous()));
    }

    private MethodDecl method(boolean isPub, boolean traitMethod) {
        Token start = peek();
        if (traitMethod && (check(TokenKind.PUB) || check(TokenKind.STATIC))) error("P001", "trait methods do not use modifiers", advance());
        if (!traitMethod && check(TokenKind.STATIC)) error("P001", "static is not a method modifier", advance());
        if (!take(TokenKind.FUNC)) error("P001", "method declarations require 'func'", peek());
        Token name = expect(TokenKind.IDENT, "method name"); requireCase(name, false, "method names");
        List<TypeParam> params = typeParams(); expect(TokenKind.LPAREN, "'(' after method name");
        boolean instance = take(TokenKind.SELF);
        if (instance) { if (take(TokenKind.COMMA)) {} }
        else if (traitMethod) error("P001", "trait methods must declare self", name);
        List<Param> arguments = new ArrayList<>();
        while (true) {
            skipLines();
            if (check(TokenKind.RPAREN) || check(TokenKind.EOF)) break;
            Token p = expect(TokenKind.IDENT, "parameter name"); requireCase(p, false, "parameter names");
            expect(TokenKind.COLON, "':' after parameter name"); TypeRef type = typeRef(); boolean variadic = take(TokenKind.SPREAD); Expr def = null;
            if (!variadic && take(TokenKind.ASSIGN)) def = expression();
            arguments.add(new Param(p.text(), type, def, variadic, p.span()));
            if (!take(TokenKind.COMMA)) break;
        }
        expect(TokenKind.RPAREN, "')' closing parameter list");
        // The return type is optional: an omitted annotation means the method
        // returns no value.  `Void` is the internal result type for that case
        // and is not a valid return type annotation.
        TypeRef ret;
        if (take(TokenKind.COLON)) {
            Token annotation = peek();
            ret = typeRef();
            if (ret.name().equals("Void")) error("P003", "Void is not a valid return type; omit the return type annotation", annotation);
        } else {
            ret = new TypeRef("Void", false, name.span());
        }
        Block body = null;
        if (check(TokenKind.LBRACE) || (check(TokenKind.NEWLINE) && nextNonLine(TokenKind.LBRACE))) { skipLines(); body = block(); }
        else terminator();
        return new MethodDecl(name.text(), isPub || traitMethod, instance, params, arguments, ret, body, spanFrom(start, previous()));
    }

    private List<TypeParam> typeParams() {
        List<TypeParam> result = new ArrayList<>();
        if (!take(TokenKind.LT)) return result;
        while (!check(TokenKind.GT) && !check(TokenKind.EOF)) {
            Token name = expect(TokenKind.IDENT, "type parameter name"); List<TypeRef> constraints = new ArrayList<>();
            if (take(TokenKind.COLON)) { constraints.add(typeRef()); while (take(TokenKind.AMPERSAND)) constraints.add(typeRef()); }
            result.add(new TypeParam(name.text(), constraints, name.span()));
            if (!take(TokenKind.COMMA)) break;
            if (check(TokenKind.GT)) break;
        }
        expect(TokenKind.GT, "'>' after type parameters"); return result;
    }

    private TypeRef typeRef() {
        Token start = peek(); String name;
        if (take(TokenKind.SELF_TYPE)) name = "Self"; else name = dottedName("type name");
        List<TypeRef> args = new ArrayList<>(); boolean explicitArguments = false;
        if (take(TokenKind.LT)) {
            explicitArguments = true;
            while (!check(TokenKind.GT) && !check(TokenKind.EOF)) {
                args.add(typeRef());
                if (!take(TokenKind.COMMA)) break;
                if (check(TokenKind.GT)) break;
            }
            expect(TokenKind.GT, "'>' closing generic type");
        }
        boolean nullable = take(TokenKind.QUESTION_MARK); return new TypeRef(name, args, nullable, explicitArguments, spanFrom(start, previous()));
    }

    private Block block() {
        Token start = expect(TokenKind.LBRACE, "'{' opening block"); List<Stmt> statements = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) { skipLines(); if (check(TokenKind.RBRACE)) break; statements.add(statement()); }
        expect(TokenKind.RBRACE, "'}' closing block"); return new Block(List.copyOf(statements), spanFrom(start, previous()));
    }

    private Stmt statement() {
        Token start = peek();
        if (take(TokenKind.RETURN)) { Expr e = (check(TokenKind.NEWLINE) || check(TokenKind.SEMICOLON) || check(TokenKind.RBRACE)) ? null : expression(); terminator(); return new ReturnStmt(e, spanFrom(start, previous())); }
        if (take(TokenKind.IF)) return parseIf(start);
        if (take(TokenKind.WHILE)) { Expr c = expression(); skipLines(); Block b = block(); return new WhileStmt(c, b, spanFrom(start, previous())); }
        if (take(TokenKind.FOR)) { Token n = expect(TokenKind.IDENT, "loop variable name"); expect(TokenKind.IN, "'in' in for loop"); Expr it = expression(); if (take(TokenKind.RANGE_INCLUSIVE)) it = new RangeExpr(it, expression(), true, spanFrom(it.span(), previous().span())); skipLines(); Block b = block(); return new ForStmt(n.text(), it, b, spanFrom(start, previous())); }
        if (take(TokenKind.SWITCH)) return parseSwitch(start);
        if (take(TokenKind.TRY)) return parseTry(start);
        if (take(TokenKind.ATOMIC)) return parseAtomic(start);
        if (take(TokenKind.THROW)) { Expr e = expression(); terminator(); return new ThrowStmt(e, spanFrom(start, previous())); }
        if (take(TokenKind.BREAK)) { terminator(); return new BreakStmt(start.span()); }
        if (take(TokenKind.CONTINUE)) { terminator(); return new ContinueStmt(start.span()); }
        if (take(TokenKind.LBRACE)) { pos--; Block b = block(); terminator(); return new BlockStmt(b, b.span()); }
        if (take(TokenKind.LET)) return variable(start, false);
        if (take(TokenKind.VAR)) return variable(start, true);
        if (check(TokenKind.IDENT) && peek(1).kind() == TokenKind.COLON) error("P001", "variable declarations require 'let' or 'var'", peek());
        Expr e = expression();
        if (isAssignment(peek().kind())) { Token op = advance(); Expr rhs = expression(); e = op.kind() == TokenKind.ASSIGN ? new AssignExpr(e, rhs, spanFrom(start, previous())) : new UpdateExpr(updateOp(op.kind()), e, rhs, spanFrom(start, previous())); }
        terminator(); return new ExprStmt(e, spanFrom(start, previous()));
    }

    private Stmt parseIf(Token start) {
        Expr condition = expression(); skipLines(); Block then = block(); skipLines();
        Stmt otherwise = null;
        if (take(TokenKind.ELSE)) { skipLines(); otherwise = take(TokenKind.IF) ? parseIf(previous()) : new BlockStmt(block(), previous().span()); }
        return new IfStmt(condition, then, otherwise, spanFrom(start, previous()));
    }

    private Stmt parseSwitch(Token start) {
        Expr subject = expression(); skipLines(); expect(TokenKind.LBRACE, "'{' after switch subject"); List<SwitchCase> cases = new ArrayList<>();
        while (true) {
            skipLines();
            if (check(TokenKind.RBRACE) || check(TokenKind.EOF)) break;
            boolean def = take(TokenKind.DEFAULT); List<Expr> values = new ArrayList<>();
            if (!def) { expect(TokenKind.CASE, "'case' or 'default'"); values.add(expression()); while (take(TokenKind.COMMA)) values.add(expression()); }
            expect(TokenKind.COLON, "':' after switch case"); skipLines(); Block body = block(); cases.add(new SwitchCase(values, def, body, body.span()));
        }
        expect(TokenKind.RBRACE, "'}' closing switch"); return new SwitchStmt(subject, cases, spanFrom(start, previous()));
    }

    private Stmt parseTry(Token start) {
        skipLines(); Block body = block(); List<CatchClause> catches = new ArrayList<>(); skipLines();
        while (true) { skipLines(); if (!take(TokenKind.CATCH)) break; expect(TokenKind.LPAREN, "'(' after catch"); Token n = expect(TokenKind.IDENT, "catch variable"); expect(TokenKind.COLON, "':' after catch variable"); TypeRef t = typeRef(); expect(TokenKind.RPAREN, "')' after catch type"); skipLines(); Block b = block(); catches.add(new CatchClause(n.text(), t, b, spanFrom(n, previous()))); }
        skipLines(); Block fin = null; if (take(TokenKind.FINALLY)) { skipLines(); fin = block(); }
        if (catches.isEmpty() && fin == null) error("P001", "try requires catch or finally", start);
        return new TryStmt(body, catches, fin, spanFrom(start, previous()));
    }

    private VarDecl variable(Token start, boolean isVar) {
        Token n = expect(TokenKind.IDENT, "variable name"); requireCase(n, false, "variable names"); expect(TokenKind.COLON, "':' after variable name"); TypeRef type = typeRef(); Expr init = take(TokenKind.ASSIGN) ? expression() : null; terminator();
        return new VarDecl(n.text(), type, isVar, init, spanFrom(start, previous()));
    }

    private Stmt parseAtomic(Token start) {
        skipLines();
        expect(TokenKind.LPAREN, "'(' after atomic");
        List<Expr> targets = new ArrayList<>();
        while (true) {
            skipLines();
            if (check(TokenKind.RPAREN) || check(TokenKind.EOF)) break;
            targets.add(expression());
            if (!take(TokenKind.COMMA)) break;
        }
        expect(TokenKind.RPAREN, "')' closing atomic target list");
        if (targets.isEmpty()) error("P004", "atomic requires at least one target", start);
        skipLines();
        Block body = block();
        return new AtomicStmt(List.copyOf(targets), body, spanFrom(start, previous()));
    }

    private Expr expression() { return coalesce(); }
    private Expr coalesce() { Expr e = or(); while (takeAfterContinuation(TokenKind.COALESCE)) e = new CoalesceExpr(e, or(), spanFrom(e.span(), previous().span())); return e; }
    private Expr or() { Expr e = and(); while (takeAfterContinuation(TokenKind.OR)) e = new BinaryExpr(BinaryOp.OR, e, and(), e.span()); return e; }
    private Expr and() { Expr e = equality(); while (takeAfterContinuation(TokenKind.AND)) e = new BinaryExpr(BinaryOp.AND, e, equality(), e.span()); return e; }
    private Expr equality() { Expr e = relational(); while (true) { TokenKind k = continuation(); if (k != TokenKind.EQ && k != TokenKind.NE) break; advance(); e = new BinaryExpr(k == TokenKind.EQ ? BinaryOp.EQ : BinaryOp.NE, e, relational(), e.span()); } return e; }
    private Expr relational() { Expr e = additive(); while (true) { TokenKind k = continuation(); BinaryOp op = switch (k) { case LT -> BinaryOp.LT; case LE -> BinaryOp.LE; case GT -> BinaryOp.GT; case GE -> BinaryOp.GE; default -> null; }; if (op == null) break; advance(); e = new BinaryExpr(op, e, additive(), e.span()); } return e; }
    private Expr additive() { Expr e = multiplicative(); while (true) { TokenKind k = continuation(); BinaryOp op = switch (k) { case PLUS -> BinaryOp.ADD; case MINUS -> BinaryOp.SUB; case RANGE -> BinaryOp.CONCAT; default -> null; }; if (op == null) break; advance(); Expr r = multiplicative(); e = new BinaryExpr(op, e, r, spanFrom(e.span(), r.span())); } return e; }
    private Expr multiplicative() { Expr e = unary(); while (true) { TokenKind k = continuation(); BinaryOp op = switch (k) { case STAR -> BinaryOp.MUL; case SLASH -> BinaryOp.DIV; case PERCENT -> BinaryOp.MOD; default -> null; }; if (op == null) break; advance(); Expr r = unary(); e = new BinaryExpr(op, e, r, spanFrom(e.span(), r.span())); } return e; }
    private Expr unary() { skipLeadingLines(); if (take(TokenKind.MINUS)) { Token s = previous(); return new UnaryExpr(UnaryOp.NEG, unary(), s.span()); } if (take(TokenKind.BANG)) { Token s = previous(); return new UnaryExpr(UnaryOp.NOT, unary(), s.span()); } return postfix(); }

    private Expr postfix() {
        Expr e = primary();
        while (true) {
            if (take(TokenKind.LPAREN)) { List<Arg> args = arguments(); skipLines(); expect(TokenKind.RPAREN, "')' closing call"); e = new CallExpr(e, args, e.span()); }
            else if (take(TokenKind.DOT)) { Token n = expect(TokenKind.IDENT, "member name after '.'"); e = new MemberExpr(e, n.text(), n.span()); }
            else break;
        }
        return e;
    }

    private List<Arg> arguments() {
        List<Arg> result = new ArrayList<>();
        while (true) {
            skipLines();
            if (check(TokenKind.RPAREN) || check(TokenKind.EOF)) break;
            Token s = peek(); String name = null; Expr e;
            if (check(TokenKind.IDENT) && peek(1).kind() == TokenKind.COLON) { name = advance().text(); advance(); e = expression(); }
            else { boolean spread = take(TokenKind.SPREAD); e = expression(); result.add(new Arg(name, e, spread, s.span())); if (!take(TokenKind.COMMA)) break; continue; }
            result.add(new Arg(name, e, false, s.span())); if (!take(TokenKind.COMMA)) break;
        }
        return result;
    }

    private Expr primary() {
        skipLeadingLines(); Token t = peek();
        if (take(TokenKind.INT)) return new Literal(LiteralKind.INT, t.text(), t.span());
        if (take(TokenKind.REAL)) return new Literal(LiteralKind.REAL, t.text(), t.span());
        if (take(TokenKind.STRING)) return new Literal(LiteralKind.STRING, t.text(), t.span());
        if (take(TokenKind.CHAR)) return new Literal(LiteralKind.CHAR, t.text(), t.span());
        if (take(TokenKind.TRUE)) return new Literal(LiteralKind.BOOLEAN, "true", t.span());
        if (take(TokenKind.FALSE)) return new Literal(LiteralKind.BOOLEAN, "false", t.span());
        if (take(TokenKind.NULL)) return new Literal(LiteralKind.NULL, "null", t.span());
        if (take(TokenKind.SELF)) return new NameExpr("self", t.span());
        if (take(TokenKind.SELF_TYPE)) {
            if (take(TokenKind.LBRACE)) return selfInit(t);
            expect(TokenKind.DOT, "'.' after Self"); Token n = expect(TokenKind.IDENT, "static member name"); return new StaticExpr(new TypeRef("Self", false, t.span()), n.text(), spanFrom(t, n));
        }
        if (take(TokenKind.LPAREN)) { Expr e = expression(); expect(TokenKind.RPAREN, "')'"); return e; }
        if (take(TokenKind.LBRACKET)) { List<Expr> es = new ArrayList<>(); while (true) { skipLines(); if (check(TokenKind.RBRACKET) || check(TokenKind.EOF)) break; es.add(expression()); if (!take(TokenKind.COMMA)) break; } expect(TokenKind.RBRACKET, "']'"); return new ListExpr(es, t.span()); }
        if (take(TokenKind.LBRACE)) { List<MapEntry> es = new ArrayList<>(); while (true) { skipLines(); if (check(TokenKind.RBRACE) || check(TokenKind.EOF)) break; Expr k = expression(); expect(TokenKind.COLON, "':' in map literal"); Expr v = expression(); es.add(new MapEntry(k, v)); if (!take(TokenKind.COMMA)) break; } expect(TokenKind.RBRACE, "'}'"); return new MapExpr(es, t.span()); }
        if (take(TokenKind.MATCH)) return match(t);
        if (take(TokenKind.IDENT)) {
            String name = t.text();
            List<TypeRef> args = List.of(); boolean explicitArguments = false;
            // A less-than after a lowercase value is relational syntax, not a
            // generic argument list.  Only type-like names may begin a
            // qualified static access here, so avoid speculative parsing that
            // would turn `n < 2` into a malformed type reference.
            if (Character.isUpperCase(name.charAt(0)) && check(TokenKind.LT)) { args = typeArguments(); explicitArguments = true; }
            if (!args.isEmpty() || Character.isUpperCase(name.charAt(0))) {
                if (take(TokenKind.DOT)) { Token member = expect(TokenKind.IDENT, "static member name"); return new StaticExpr(new TypeRef(name, args, false, explicitArguments, t.span()), member.text(), spanFrom(t, member)); }
            }
            return new NameExpr(name, t.span());
        }
        error("P001", "expected expression", t); advance(); return new Literal(LiteralKind.NULL, "null", t.span());
    }

    private List<TypeRef> typeArguments() {
        expect(TokenKind.LT, "'<'"); List<TypeRef> args = new ArrayList<>();
        while (!check(TokenKind.GT) && !check(TokenKind.EOF)) {
            args.add(typeRef());
            if (!take(TokenKind.COMMA)) break;
            if (check(TokenKind.GT)) break;
        }
        expect(TokenKind.GT, "'>'"); return args;
    }

    private SelfInitExpr selfInit(Token start) {
        List<FieldInit> fields = new ArrayList<>();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            skipLines();
            if (check(TokenKind.RBRACE)) break;
            Token n = expect(TokenKind.IDENT, "field name"); expect(TokenKind.COLON, "':' after field name"); Expr v = expression(); fields.add(new FieldInit(n.text(), v, n.span()));
            if (!take(TokenKind.COMMA)) break;
        }
        expect(TokenKind.RBRACE, "'}' closing construction"); return new SelfInitExpr(fields, spanFrom(start, previous()));
    }

    private MatchExpr match(Token start) {
        Expr subject = expression(); skipLines(); expect(TokenKind.LBRACE, "'{' after match subject"); List<MatchArm> arms = new ArrayList<>();
        while (true) { skipLines(); if (check(TokenKind.RBRACE) || check(TokenKind.EOF)) break; Token s = peek(); Pattern p = pattern(); Expr guard = null; expect(TokenKind.ARROW, "'=>' after match pattern"); Expr body = expression(); arms.add(new MatchArm(p, guard, body, spanFrom(s.span(), body.span()))); if (take(TokenKind.COMMA)) error("P001", "match arms must be separated by newlines", previous()); else skipLines(); }
        expect(TokenKind.RBRACE, "'}' closing match"); return new MatchExpr(subject, arms, spanFrom(start, previous()));
    }

    private Pattern pattern() {
        Token t = peek();
        if (take(TokenKind.INT) || take(TokenKind.REAL) || take(TokenKind.STRING) || take(TokenKind.CHAR) || take(TokenKind.TRUE) || take(TokenKind.FALSE)) {
            LiteralKind kind = switch (t.kind()) { case INT -> LiteralKind.INT; case REAL -> LiteralKind.REAL; case STRING -> LiteralKind.STRING; case CHAR -> LiteralKind.CHAR; default -> LiteralKind.BOOLEAN; }; return new LiteralPattern(new Literal(kind, t.text(), t.span()));
        }
        if (take(TokenKind.LBRACKET)) { List<Pattern> p = new ArrayList<>(); while (true) { skipLines(); if (check(TokenKind.RBRACKET) || check(TokenKind.EOF)) break; p.add(pattern()); if (!take(TokenKind.COMMA)) break; } expect(TokenKind.RBRACKET, "']' in list pattern"); return new ListPattern(p, t.span()); }
        if (take(TokenKind.IDENT)) { if (t.text().equals("_")) return new WildcardPattern(t.span()); if (take(TokenKind.DOT)) { Token v = expect(TokenKind.IDENT, "variant name"); List<Pattern> sub = patternPayload(); return new VariantPattern(t.text(), v.text(), sub, t.span()); } if (check(TokenKind.LPAREN)) { List<Pattern> sub = patternPayload(); return new VariantPattern(null, t.text(), sub, t.span()); } return new BindPattern(t.text(), t.span()); }
        error("P001", "expected pattern", t); advance(); return new WildcardPattern(t.span());
    }

    private List<Pattern> patternPayload() { List<Pattern> p = new ArrayList<>(); if (!take(TokenKind.LPAREN)) return p; while (true) { skipLines(); if (check(TokenKind.RPAREN) || check(TokenKind.EOF)) break; p.add(pattern()); if (!take(TokenKind.COMMA)) break; } expect(TokenKind.RPAREN, "')' after pattern payload"); return p; }

    private boolean isAssignment(TokenKind k) { return k == TokenKind.ASSIGN || k == TokenKind.PLUS_ASSIGN || k == TokenKind.MINUS_ASSIGN || k == TokenKind.STAR_ASSIGN || k == TokenKind.SLASH_ASSIGN || k == TokenKind.PERCENT_ASSIGN || k == TokenKind.RANGE_INCLUSIVE; }
    private BinaryOp updateOp(TokenKind k) { return switch (k) { case PLUS_ASSIGN -> BinaryOp.ADD; case MINUS_ASSIGN -> BinaryOp.SUB; case STAR_ASSIGN -> BinaryOp.MUL; case SLASH_ASSIGN -> BinaryOp.DIV; case PERCENT_ASSIGN -> BinaryOp.MOD; default -> BinaryOp.CONCAT; }; }
    private TokenKind continuation() { while (check(TokenKind.NEWLINE) && isOperator(peek(1).kind())) advance(); return peek().kind(); }
    private boolean takeAfterContinuation(TokenKind kind) { int old = pos; while (check(TokenKind.NEWLINE)) { if (!isOperator(peek(1).kind())) break; advance(); } if (check(kind)) { advance(); return true; } pos = old; return false; }
    private boolean isOperator(TokenKind k) { return switch (k) { case COALESCE, OR, AND, EQ, NE, LT, LE, GT, GE, PLUS, MINUS, RANGE, RANGE_INCLUSIVE, STAR, SLASH, PERCENT -> true; default -> false; }; }
    private void skipLeadingLines() { while (check(TokenKind.NEWLINE)) advance(); }
    private void skipLines() { while (check(TokenKind.NEWLINE) || check(TokenKind.SEMICOLON)) advance(); }
    private void terminator() { if (take(TokenKind.SEMICOLON)) { skipLines(); return; } if (check(TokenKind.NEWLINE)) skipLines(); }
    private String dottedName(String what) { Token first = expect(TokenKind.IDENT, what); StringBuilder s = new StringBuilder(first.text()); while (take(TokenKind.DOT)) s.append('.').append(expect(TokenKind.IDENT, what).text()); return s.toString(); }
    private boolean nextNonLine(TokenKind kind) { int i = pos; while (i < tokens.size() && tokens.get(i).kind() == TokenKind.NEWLINE) i++; return i < tokens.size() && tokens.get(i).kind() == kind; }
    private boolean isIdent(String s) { return check(TokenKind.IDENT) && peek().text().equals(s); }
    private Token previous() { return tokens.get(Math.max(0, pos - 1)); }
    private Token peek() { return tokens.get(Math.min(pos, tokens.size() - 1)); }
    private Token peek(int n) { return tokens.get(Math.min(pos + n, tokens.size() - 1)); }
    private boolean check(TokenKind k) { return peek().kind() == k; }
    private boolean take(TokenKind k) { if (!check(k)) return false; advance(); return true; }
    private Token advance() { return tokens.get(pos++); }
    private Token expect(TokenKind k, String what) { if (check(k)) return advance(); error("P001", "expected " + what + ", found '" + peek().text() + "'", peek()); return new Token(k, "", peek().span()); }
    private void requireCase(Token t, boolean upper, String category) { if (!t.text().isEmpty() && Character.isUpperCase(t.text().charAt(0)) != upper) error("P002", category + " must start with an " + (upper ? "uppercase" : "lowercase") + " character", t); }
    private Span spanFrom(Token a, Token b) { return new Span(a.span().file(), a.span().start(), b.span().end(), a.span().line(), a.span().column()); }
    private Span spanFrom(Span a, Span b) { return new Span(a.file(), a.start(), b.end(), a.line(), a.column()); }
    private void error(String code, String message, Token at) { diagnostics.add(new Diagnostic(code, message, at.span())); }
    private void recoverTopLevel() { while (!check(TokenKind.EOF) && !check(TokenKind.STRUCT) && !check(TokenKind.TRAIT) && !check(TokenKind.ENUM)) advance(); }
    private void recoverMember() { while (!check(TokenKind.EOF) && !check(TokenKind.NEWLINE) && !check(TokenKind.RBRACE)) advance(); skipLines(); }
}
