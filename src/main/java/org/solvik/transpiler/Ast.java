package org.solvik.transpiler;

import java.util.List;

import org.solvik.transpiler.language.Language.BinaryOp;
import org.solvik.transpiler.language.Language.LiteralKind;
import org.solvik.transpiler.language.Language.UnaryOp;

/** Immutable syntax tree. No node stores an unparsed source fragment. */
public final class Ast {
    private Ast() {}

    public record CompilationUnit(String packageName, List<UseDecl> uses, List<Decl> declarations, Span span) {}
    public record UseDecl(String scheme, String path, String alias, Span span) {}

    public sealed interface Decl permits StructDecl, TraitDecl, EnumDecl { String name(); List<TypeParam> typeParams(); Span span(); }
    public record TypeParam(String name, List<TypeRef> constraints, Span span) {}
    public record StructDecl(String name, List<TypeParam> typeParams, List<TypeRef> implementsTypes,
                             List<DelegateDecl> delegates, List<FieldDecl> fields, List<MethodDecl> methods,
                             Block staticBlock, Span span) implements Decl {}
    public record TraitDecl(String name, List<TypeParam> typeParams, List<TypeRef> extendsTypes,
                                List<MethodDecl> methods, Span span) implements Decl {}
    public record EnumDecl(String name, List<TypeParam> typeParams, List<VariantDecl> variants, Span span) implements Decl {}
    public record VariantDecl(String name, TypeRef payload, Span span) {}
    public record DelegateDecl(TypeRef traitType, String field, Span span) {}
    public record FieldDecl(String name, TypeRef type, boolean isVar, boolean isStatic, Expr initializer, Span span) {}
    public record MethodDecl(String name, boolean isPub, boolean instance, List<TypeParam> typeParams,
                             List<Param> params, TypeRef returnType, Block body, Span span) {}
    public record Param(String name, TypeRef type, Expr defaultValue, boolean variadic, Span span) {}
    public record TypeRef(String name, List<TypeRef> args, boolean nullable, boolean explicitArguments, Span span) {
        public TypeRef(String name, List<TypeRef> args, boolean nullable, Span span) { this(name, args, nullable, !args.isEmpty(), span); }
        public TypeRef(String name, boolean nullable, Span span) { this(name, List.of(), nullable, false, span); }
    }
    public record Block(List<Stmt> statements, Span span) {}

    public sealed interface Stmt permits VarDecl, ExprStmt, ReturnStmt, IfStmt, WhileStmt, ForStmt,
            SwitchStmt, TryStmt, ThrowStmt, BreakStmt, ContinueStmt, BlockStmt, AtomicStmt { Span span(); }
    public record VarDecl(String name, TypeRef type, boolean isVar, Expr initializer, Span span) implements Stmt {}
    public record ExprStmt(Expr expression, Span span) implements Stmt {}
    public record ReturnStmt(Expr value, Span span) implements Stmt {}
    public record IfStmt(Expr condition, Block thenBlock, Stmt elseBranch, Span span) implements Stmt {}
    public record WhileStmt(Expr condition, Block body, Span span) implements Stmt {}
    public record ForStmt(String name, Expr iterable, Block body, Span span) implements Stmt {}
    public record SwitchStmt(Expr subject, List<SwitchCase> cases, Span span) implements Stmt {}
    public record SwitchCase(List<Expr> values, boolean isDefault, Block body, Span span) {}
    public record TryStmt(Block body, List<CatchClause> catches, Block finallyBlock, Span span) implements Stmt {}
    public record CatchClause(String name, TypeRef type, Block body, Span span) {}
    public record ThrowStmt(Expr value, Span span) implements Stmt {}
    public record BreakStmt(Span span) implements Stmt {}
    public record ContinueStmt(Span span) implements Stmt {}
    public record BlockStmt(Block block, Span span) implements Stmt {}

    /** {@code atomic(target, ...) { ... }}: exclusive monitor scope over struct instances. */
    public record AtomicStmt(List<Expr> targets, Block block, Span span) implements Stmt {}

    public sealed interface Expr permits Literal, NameExpr, ListExpr, MapExpr, CallExpr, MemberExpr,
            StaticExpr, BinaryExpr, UnaryExpr, CoalesceExpr, RangeExpr, MatchExpr, SelfInitExpr,
            AssignExpr, UpdateExpr { Span span(); }
    public record Literal(LiteralKind kind, String text, Span span) implements Expr {}
    public record NameExpr(String name, Span span) implements Expr {}
    public record ListExpr(List<Expr> elements, Span span) implements Expr {}
    public record MapEntry(Expr key, Expr value) {}
    public record MapExpr(List<MapEntry> entries, Span span) implements Expr {}
    public record Arg(String name, Expr expression, boolean spread, Span span) {}
    public record CallExpr(Expr callee, List<Arg> arguments, Span span) implements Expr {}
    public record MemberExpr(Expr object, String name, Span span) implements Expr {}
    public record StaticExpr(TypeRef type, String name, Span span) implements Expr {}
    public record BinaryExpr(BinaryOp op, Expr left, Expr right, Span span) implements Expr {}
    public record UnaryExpr(UnaryOp op, Expr operand, Span span) implements Expr {}
    public record CoalesceExpr(Expr left, Expr right, Span span) implements Expr {}
    public record RangeExpr(Expr start, Expr end, boolean inclusive, Span span) implements Expr {}
    public record MatchExpr(Expr subject, List<MatchArm> arms, Span span) implements Expr {}
    public record MatchArm(Pattern pattern, Expr guard, Expr body, Span span) {}
    public sealed interface Pattern permits WildcardPattern, LiteralPattern, BindPattern, VariantPattern, ListPattern {}
    public record WildcardPattern(Span span) implements Pattern {}
    public record LiteralPattern(Literal literal) implements Pattern {}
    public record BindPattern(String name, Span span) implements Pattern {}
    public record VariantPattern(String enumName, String name, List<Pattern> payload, Span span) implements Pattern {}
    public record ListPattern(List<Pattern> elements, Span span) implements Pattern {}
    public record SelfInitExpr(List<FieldInit> fields, Span span) implements Expr {}
    public record FieldInit(String name, Expr value, Span span) {}
    public record AssignExpr(Expr target, Expr value, Span span) implements Expr {}
    public record UpdateExpr(BinaryOp op, Expr target, Expr value, Span span) implements Expr {}
}
