package dev.solvik.compiler.ast;

import dev.solvik.compiler.diagnostic.SourcePos;
import dev.solvik.compiler.lexer.NumericSuffix;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The Solvik abstract syntax tree.
 *
 * <p>Grouped as nested records to keep the node set readable in one place. Every node carries the
 * position of its first token so that later phases can report diagnostics against the source.
 *
 * <p>Shapes follow {@code LANGUAGE.md}: a program is a sequence of type declarations, function
 * declarations, and top-level statements that execute in source order (§3); nullability, control
 * and the operator set follow §3 and §7.
 */
public final class Ast {

    private Ast() {
    }

    /** Common supertype so nodes can be handled generically. */
    public interface Node {
        SourcePos pos();
    }

    // ------------------------------------------------------------- types

    /**
     * A written type: a named type, or an array of a written element type.
     *
     * <p>{@code ?} is a type suffix that marks the type immediately to its left as nullable, so
     * {@code String?[2]} is an array of nullable strings and {@code String[2]?} is a nullable array
     * of non-null strings ({@code LANGUAGE.md} §2.1, §5). An array written {@code T[N]} carries the
     * fixed length {@code N}; {@code T[]} is the size-erased array type.
     *
     * <p>Both are structural, so no type is ever stored as preformatted text.
     */
    public sealed interface TypeRef extends Node {

        /** Whether {@code ?} followed this type. */
        boolean nullable();

        /** A type name, its written arguments, and the nullability written after it. */
        record Named(String name, List<TypeRef> arguments, boolean nullable, SourcePos pos)
                implements TypeRef {

            public Named {
                arguments = List.copyOf(arguments);
            }

            /** A non-nullable named type with no arguments. */
            public Named(String name, SourcePos pos) {
                this(name, List.of(), false, pos);
            }

            @Override
            public String toString() {
                return name + (arguments.isEmpty() ? ""
                        : arguments.stream().map(Object::toString)
                                .collect(Collectors.joining(", ", "<", ">")))
                        + (nullable ? "?" : "");
            }
        }

        /**
         * An array type.
         *
         * @param element the element type, which carries its own nullability
         * @param size    the written fixed length, or {@code null} for the size-erased {@code T[]}
         */
        record Array(TypeRef element, Expr size, boolean nullable, SourcePos pos)
                implements TypeRef {

            @Override
            public String toString() {
                String written = size instanceof IntLit literal ? literal.text()
                        : size == null ? "" : "…";
                return element + "[" + written + "]" + (nullable ? "?" : "");
            }

            /** This type with {@code ?} applied where it was written. */
            public Array asNullable() {
                return new Array(element, size, true, pos);
            }
        }
    }

    // ------------------------------------------------------------- program

    /**
     * A parsed source file.
     *
     * @param types      user-defined type declarations outside any module, in source order
     * @param functions  top-level function declarations outside any module, in source order
     * @param statements top-level statements, in source order; these are what execute
     * @param modules   {@code module} blocks, in source order; blocks of one name merge (§2)
     * @param includes  {@code include} entries, in source order; the loader resolves them
     * @param aliases   {@code alias} entries, in source order; each is local to its source file
     * @param pos       the position of the first token in the file
     */
    public record Program(List<TypeDecl> types, List<FuncDecl> functions,
                          List<Stmt> statements, List<ModuleDecl> modules,
                          List<IncludeDecl> includes, List<AliasDecl> aliases, SourcePos pos)
            implements Node {

        /** A program without modules, includes, or aliases. */
        public Program(List<TypeDecl> types, List<FuncDecl> functions, List<Stmt> statements,
                SourcePos pos) {
            this(types, functions, statements, List.of(), List.of(), List.of(), pos);
        }

        /** A program with modules but no includes or aliases. */
        public Program(List<TypeDecl> types, List<FuncDecl> functions, List<Stmt> statements,
                List<ModuleDecl> modules, SourcePos pos) {
            this(types, functions, statements, modules, List.of(), List.of(), pos);
        }
    }

    /**
     * One path inside an {@code include { ... }} block (§3, "Source files").
     *
     * <p>The path is a string literal resolved relative to the file that writes the block, and the
     * included file's declarations join this program. Only the entry file's statements execute.
     */
    public record IncludeDecl(String path, SourcePos pos) implements Node {
    }

    /**
     * One entry inside an {@code alias { ... }} block: a canonical target and a local name.
     *
     * <p>{@code target} is a fully qualified module, type, or trait name, and {@code localName} is
     * the spelling this source file may use for it. Aliases are file-local conveniences: they add
     * no nominal identity and never enter the merged module namespace.
     *
     * @param target    the canonical qualified name being aliased
     * @param localName the file-local spelling
     * @param targetPos the position of the target, where resolution problems are reported
     * @param namePos   the position of the local name, where collisions are reported
     */
    public record AliasDecl(String target, String localName, SourcePos targetPos, SourcePos namePos)
            implements Node {

        @Override
        public SourcePos pos() {
            return targetPos;
        }
    }

    /**
     * A {@code module Name { ... }} block: a namespace of types, functions, and nested modules
     * (§2, "Modules").
     *
     * <p>A module holds no data and declares nothing that executes. Blocks with the same name
     * merge, so the checker combines them by qualified name.
     */
    public record ModuleDecl(String name, List<TypeDecl> types, List<FuncDecl> functions,
                             List<ModuleDecl> modules, SourcePos pos) implements Node {

        public ModuleDecl(String name, List<TypeDecl> types, List<FuncDecl> functions,
                SourcePos pos) {
            this(name, types, functions, List.of(), pos);
        }
    }

    // ---------------------------------------------------------- declarations

    /** Whether a nominal declaration creates values or declares an interface contract. */
    public enum TypeKind {
        TYPE, TRAIT
    }

    /**
     * A {@code static { ... }} block in a concrete type body (§3.4).
     *
     * <p>The block runs once as part of Java class initialization, interleaved with static field
     * initializers in source order. {@code fieldIndex} is the number of field declarations parsed
     * before the block, which is all the ordering the generated {@code <clinit>} needs.
     *
     * @param body       the statements to run
     * @param fieldIndex how many fields precede the block in source order
     * @param pos        the position of the {@code static} keyword
     */
    public record StaticInitializer(List<Stmt> body, int fieldIndex, SourcePos pos)
            implements Node {

        public StaticInitializer {
            body = List.copyOf(body);
        }
    }

    /**
     * A type declaration (§2, "User-defined types").
     *
     * @param isPublic whether {@code pub} makes the type visible outside its file; a type inside a
     *                 module is private to that file unless marked
     * @param staticInitializers {@code static { ... }} blocks, in source order
     */
    public record TypeDecl(String name, List<String> typeParameters, List<FieldDecl> fields,
                           List<FuncDecl> methods, boolean isPublic, SourcePos pos,
                           String nativeClass, TypeKind kind,
                           List<TypeRef> implementedTraits,
                           List<StaticInitializer> staticInitializers) implements Node {

        public TypeDecl(String name, List<String> typeParameters, List<FieldDecl> fields,
                List<FuncDecl> methods, boolean isPublic, SourcePos pos, String nativeClass,
                TypeKind kind, List<TypeRef> implementedTraits) {
            this(name, typeParameters, fields, methods, isPublic, pos, nativeClass, kind,
                    implementedTraits, List.of());
        }

        public TypeDecl(String name, List<String> typeParameters, List<FieldDecl> fields,
                List<FuncDecl> methods, boolean isPublic, SourcePos pos, String nativeClass) {
            this(name, typeParameters, fields, methods, isPublic, pos, nativeClass,
                    TypeKind.TYPE, List.of(), List.of());
        }

        public TypeDecl(String name, List<String> parameters, List<FieldDecl> fields,
                List<FuncDecl> methods, boolean isPublic, SourcePos pos) {
            this(name, parameters, fields, methods, isPublic, pos, null);
        }

        public TypeDecl(String name, List<String> typeParameters, List<FieldDecl> fields,
                List<FuncDecl> methods, SourcePos pos) {
            this(name, typeParameters, fields, methods, false, pos);
        }

        public TypeDecl(String name, List<FieldDecl> fields, List<FuncDecl> methods, SourcePos pos) {
            this(name, List.of(), fields, methods, false, pos);
        }

        public boolean isTrait() {
            return kind == TypeKind.TRAIT;
        }
    }

    /**
     * A field declaration: {@code mut value: T? = Null} or {@code pub count := 0} (§3.3).
     *
     * <p>Every field declaration has an initializer: there are no uninitialized field slots.
     *
     * @param name             the field name
     * @param type             the explicitly written type, or {@code null} when it is inferred
     * @param nullableInferred whether the declaration wrote {@code name? := ...}, which asks for an
     *                         inferred nullable type
     * @param initializer      the required initializer expression
     * @param isPublic         whether {@code pub} made the field readable from outside the type
     * @param isMutable        whether {@code mut} allows writing it after construction
     * @param isStatic         whether {@code static} makes the field belong to the type
     * @param pos              the position of the first modifier or the name
     */
    public record FieldDecl(String name, TypeRef type, boolean nullableInferred, Expr initializer,
                            boolean isPublic, boolean isMutable, boolean isStatic, SourcePos pos)
            implements Node {

        /** A field without {@code static}. */
        public FieldDecl(String name, TypeRef type, boolean nullableInferred, Expr initializer,
                boolean isPublic, boolean isMutable, SourcePos pos) {
            this(name, type, nullableInferred, initializer, isPublic, isMutable, false, pos);
        }

        /** A private, immutable, explicitly typed field, which is the default. */
        public FieldDecl(String name, TypeRef type, Expr initializer, SourcePos pos) {
            this(name, type, false, initializer, false, false, false, pos);
        }
    }

    /**
     * A function or method declaration.
     *
     * @param name       the declared name; for a constructor this equals the enclosing type name
     * @param params     positional parameters
     * @param returnType the written return type, or {@code null} when {@code ->} is absent
     * @param body       the statement block, empty both for an absent body and an empty block
     * @param isStatic   whether {@code static} makes the function belong to the type, without self
     * @param hasBody    whether source supplied a block; required to distinguish a trait contract
     *                   from an explicitly empty default function
     * @param isNative   whether {@code native} declared a bodyless member resolved through the
     *                   compiler's binding registry; only compiler-packaged sources may declare one
     */
    public record FuncDecl(String name, List<Param> params, TypeRef returnType,
                           List<Stmt> body, boolean isPublic, boolean isStatic, SourcePos pos,
                           String nativeName, boolean hasBody, boolean isNative) implements Node {

        /** A function with an explicit native Java name and an implied body. */
        public FuncDecl(String name, List<Param> params, TypeRef returnType, List<Stmt> body,
                boolean isPublic, boolean isStatic, SourcePos pos, String nativeName) {
            this(name, params, returnType, body, isPublic, isStatic, pos, nativeName, true, false);
        }

        /** A parsed declaration with an explicit body-presence marker. */
        public FuncDecl(String name, List<Param> params, TypeRef returnType, List<Stmt> body,
                boolean isPublic, boolean isStatic, SourcePos pos, String nativeName,
                boolean hasBody) {
            this(name, params, returnType, body, isPublic, isStatic, pos, nativeName, hasBody,
                    false);
        }

        /** A function without {@code static}. */
        public FuncDecl(String name, List<Param> params, TypeRef returnType, List<Stmt> body,
                boolean isPublic, SourcePos pos, String nativeName) {
            this(name, params, returnType, body, isPublic, false, pos, nativeName, true);
        }

        public FuncDecl(String name, List<Param> params, TypeRef returnType, List<Stmt> body,
                boolean isPublic, SourcePos pos) {
            this(name, params, returnType, body, isPublic, false, pos, null);
        }

        /** A declaration without {@code pub}, which is private for a method. */
        public FuncDecl(String name, List<Param> params, TypeRef returnType, List<Stmt> body,
                SourcePos pos) {
            this(name, params, returnType, body, false, false, pos, null);
        }

        public boolean isConstructor(String typeName) {
            return name.equals(typeName);
        }
    }

    public record Param(String name, TypeRef type, SourcePos pos) implements Node {
    }

    // ------------------------------------------------------------ statements

    public sealed interface Stmt extends Node {
    }

    /**
     * A local or top-level binding declaration (§3.1): {@code count := 1},
     * {@code count: Integer = 1}, {@code mut total := 0}, or {@code name? := value}.
     *
     * <p>Every declaration has an initializer: a binding's type is known where it is declared.
     *
     * @param name             the declared name; {@code ?} belongs to the declaration, not the name
     * @param type             the explicitly written type, or {@code null} when it is inferred
     * @param nullableInferred whether {@code name? := ...} asked for an inferred nullable type
     * @param initializer      the required initializer expression
     * @param isMutable        whether {@code mut} allows assignment after initialization
     */
    public record VarStmt(String name, TypeRef type, boolean nullableInferred, Expr initializer,
                          boolean isMutable, SourcePos pos) implements Stmt {

        /** An inferred, immutable declaration. */
        public VarStmt(String name, Expr initializer, SourcePos pos) {
            this(name, null, false, initializer, false, pos);
        }
    }

    /**
     * Assignment: {@code target = value} or {@code target ..= value}.
     *
     * @param operator either {@code "="} or {@code "..="}
     */
    public record AssignStmt(Expr target, String operator, Expr value, SourcePos pos) implements Stmt {
    }

    /** A call used as a statement, such as {@code println(x)}. */
    public record ExprStmt(Expr expression, SourcePos pos) implements Stmt {
    }

    /**
     * {@code if cond { } else if cond { } else { }}.
     *
     * @param branches the {@code if} branch followed by each {@code else if} branch, in order
     * @param elseBody the final {@code else} block, or {@code null} when absent
     */
    public record IfStmt(List<Branch> branches, List<Stmt> elseBody, SourcePos pos) implements Stmt {
    }

    public record Branch(Expr condition, List<Stmt> body, SourcePos pos) implements Node {
    }

    public record WhileStmt(Expr condition, List<Stmt> body, SourcePos pos) implements Stmt {
    }

    /** {@code for name in iterable { }}. */
    public record ForStmt(String name, Expr iterable, List<Stmt> body, SourcePos pos) implements Stmt {
    }

    /** {@code return expr} or bare {@code return}; {@code value} is {@code null} when bare. */
    public record ReturnStmt(Expr value, SourcePos pos) implements Stmt {
    }

    /**
     * {@code switch subject { case label { ... } ... default { ... } }} (§3, "switch").
     *
     * <p>Exactly one body runs: there is no fall-through, so no {@code break} is written or
     * needed. {@code defaultBody} is {@code null} when the switch has no {@code default}.
     */
    public record SwitchStmt(Expr subject, List<CaseClause> cases, List<Stmt> defaultBody,
            SourcePos pos) implements Stmt {
    }

    /**
     * One {@code case} of a {@link SwitchStmt}.
     *
     * <p>{@code label} is a literal; when {@code regex} is set it is a string literal naming a
     * regular expression, and the case matches when the whole subject matches it.
     */
    public record CaseClause(Expr label, boolean regex, List<Stmt> body, SourcePos pos)
            implements Node {
    }

    /**
     * {@code break}: leaves the innermost enclosing loop (§3, "Control flow").
     *
     * <p>A {@code switch} is never the target: it has no fall-through, so {@code break} always
     * means the loop, which is why generated Java targets loops by label.
     */
    public record BreakStmt(SourcePos pos) implements Stmt {
    }

    /** {@code continue}: starts the next iteration of the innermost enclosing loop. */
    public record ContinueStmt(SourcePos pos) implements Stmt {
    }

    /**
     * {@code try { } catch (e: Exception) { } finally { }} (§7.4).
     *
     * <p>A {@code try} statement has at most one {@code catch} clause, because {@code Exception} is
     * the only catchable type, and needs at least one of the two clauses: a {@code try} with
     * neither has nothing to do. {@code finally} is implied by its absence.
     *
     * @param body        the statements the clauses guard
     * @param catcher     the single {@code catch} clause, or {@code null} when absent
     * @param finallyBody the {@code finally} block, or {@code null} when absent
     */
    public record TryStmt(List<Stmt> body, CatchClause catcher, List<Stmt> finallyBody,
                          SourcePos pos) implements Stmt {
    }

    /**
     * One {@code catch (e: Exception) { }} clause.
     *
     * <p>The written type is recorded, and the checker requires it to be exactly {@code Exception}:
     * one exception type is the whole catchable surface (§7.4). The bound name is an immutable,
     * non-null value visible in the clause body only.
     */
    public record CatchClause(String name, TypeRef type, List<Stmt> body, SourcePos pos)
            implements Node {
    }

    /**
     * {@code raise exception}: raises the given {@code Exception} (§7.4).
     *
     * <p>Like {@code return}, this statement never completes normally, so what follows it in the
     * same list is unreachable.
     */
    public record RaiseStmt(Expr exception, SourcePos pos) implements Stmt {
    }

    /** A bare block {@code { ... }} treated as one statement. */
    public record BlockStmt(List<Stmt> body, SourcePos pos) implements Stmt {
    }

    /**
     * {@code sync monitor { ... }}: runs the block holding the monitor's Java monitor (§7.5).
     *
     * <p>The monitor is an ordinary expression and the block is a block, so the body is its own
     * scope and {@code return}, {@code break}, {@code continue}, and {@code raise} mean exactly
     * what they mean in a bare block. The monitor is released on every way out, so no release
     * statement is written or generated.
     *
     * @param monitor the expression naming the non-null value whose identity is the monitor
     * @param body    the guarded statements
     */
    public record SyncStmt(Expr monitor, List<Stmt> body, SourcePos pos) implements Stmt {
    }

    // ----------------------------------------------------------- expressions

    public sealed interface Expr extends Node {
    }

    /**
     * An integer-spelled numeric literal.
     *
     * @param text   the digits, without the suffix; every value consumer reads only this
     * @param suffix the uppercase suffix that fixes the literal's type, or {@link NumericSuffix#NONE}
     *               when context or the default table decides it (§4)
     */
    public record IntLit(String text, NumericSuffix suffix, SourcePos pos) implements Expr {

        public IntLit(String text, SourcePos pos) {
            this(text, NumericSuffix.NONE, pos);
        }
    }

    /** A fraction-spelled numeric literal; {@code text} keeps the digits and the point. */
    public record DecimalLit(String text, NumericSuffix suffix, SourcePos pos) implements Expr {

        public DecimalLit(String text, SourcePos pos) {
            this(text, NumericSuffix.NONE, pos);
        }
    }

    public record StringLit(String value, SourcePos pos) implements Expr {
    }

    public record BoolLit(boolean value, SourcePos pos) implements Expr {
    }

    public record NullLit(SourcePos pos) implements Expr {
    }

    /**
     * {@code outer name}: the binding of {@code name} one scope out (§3, "Scope and shadowing").
     *
     * <p>It is a qualifier rather than an expression of its own, and it resolves to the nearest
     * enclosing declaration, so a shadowed value stays reachable from the block that shadows it.
     */
    public record OuterExpr(String name, SourcePos pos) implements Expr {
    }

    /** A variable, parameter, {@code self}, or a name used as a callee. */
    public record NameExpr(String name, SourcePos pos) implements Expr {
    }

    /** A module-qualified name such as {@code geometry::Point}. */
    public record QualifiedNameExpr(String module, String name, SourcePos pos) implements Expr {

        /** The qualified spelling, {@code geometry::Point} or {@code solvik::collection::Map}. */
        public String qualifiedName() {
            return module.isEmpty() ? name : module + "::" + name;
        }
    }

    /** Explicit type arguments on a native constructor. */
    public record TypeApplyExpr(Expr target, List<TypeRef> arguments, SourcePos pos) implements Expr { }

    public record UnaryExpr(String operator, Expr operand, SourcePos pos) implements Expr {
    }

    public record BinaryExpr(Expr left, String operator, Expr right, SourcePos pos) implements Expr {
    }

    /** A call: {@code f(a, b)}, a conversion {@code Integer(x)}, or a construction {@code Point(1)}. */
    public record CallExpr(Expr callee, List<Expr> arguments, SourcePos pos) implements Expr {
    }

    /** Member access: {@code self.x}, {@code Type.staticValue}, or {@code Trait.defaultFunc}. */
    public record MemberExpr(Expr target, String name, SourcePos pos) implements Expr {
    }

    /** Indexing: {@code bytes[0]}. */
    public record IndexExpr(Expr target, Expr index, SourcePos pos) implements Expr {
    }

    /**
     * A typed array literal: {@code Type[N] { element, ... } } (§5.1).
     *
     * <p>The written type is the array type itself, so {@code Integer[2] {1, 2} } is an
     * {@code Integer[2]} and {@code Integer[2][2] { ... }} lists two inner arrays. A literal always
     * builds a non-null array; the length is part of the type.
     *
     * @param arrayType the written array type, carrying the element type and the length
     * @param elements  the listed elements, in order
     */
    public record ArrayLit(TypeRef arrayType, List<Expr> elements, SourcePos pos) implements Expr {

        public ArrayLit {
            elements = List.copyOf(elements);
        }
    }
}
