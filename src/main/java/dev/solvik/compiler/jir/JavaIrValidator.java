package dev.solvik.compiler.jir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates structural Java IR invariants before rendering.
 *
 * <p>Rendering malformed IR would produce Java that either does not compile or, worse, compiles
 * with a different meaning. These checks turn those cases into a compiler failure that names the
 * offending declaration, so a lowering bug cannot silently reach generated source.
 */
public final class JavaIrValidator {

    private static final Set<String> RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "_", "var", "yield", "record");

    private JavaIrValidator() {
    }

    /** Validates one compilation unit, throwing on the first invariant violation. */
    public static void validate(JDecl.CompilationUnit unit) {
        require(unit != null, "a compilation unit is required");
        require(!unit.packageName().isEmpty(), "a generated compilation unit needs a package");
        validatePackage(unit.packageName());
        long publicTypes = unit.types().stream()
                .filter(type -> type.modifiers().contains(JDecl.Modifier.PUBLIC))
                .count();
        require(publicTypes == 1, "a compilation unit needs exactly one public type, found "
                + publicTypes);
        require(unit.primary().modifiers().contains(JDecl.Modifier.PUBLIC),
                "the first type of a compilation unit must be its public type");
        for (JDecl.ClassDecl type : unit.types()) {
            validateType(type, unit.packageName());
        }
    }

    private static void validateType(JDecl.ClassDecl type, String packageName) {
        validateIdentifier(type.name(), "type");
        for (String parameter : type.typeParameters()) {
            validateIdentifier(parameter, "type parameter");
        }
        for (JType implemented : type.interfaces()) {
            validateTypeReference(implemented, "implemented interface of " + type.name());
        }
        if (type.superClass() != null) {
            validateTypeReference(type.superClass(), "superclass of " + type.name());
        }
        Set<String> fields = new HashSet<>();
        Set<String> methods = new HashSet<>();
        Set<String> nestedTypes = new HashSet<>();
        for (JDecl.Member member : type.members()) {
            switch (member) {
                case JDecl.Field field -> {
                    validateIdentifier(field.name(), "field");
                    require(fields.add(field.name()), "duplicate field '" + field.name()
                            + "' in " + type.name());
                    validateTypeReference(field.type(), "field " + field.name());
                }
                case JDecl.Constructor constructor -> {
                    for (JDecl.Parameter parameter : constructor.parameters()) {
                        validateParameter(parameter, "constructor of " + type.name());
                    }
                }
                case JDecl.StaticInitializer initializer -> {
                    require(type.kind() == JDecl.Kind.CLASS,
                            "static initializer of " + type.name() + " requires a class");
                    validateStatements(initializer.body(), "static initializer of " + type.name());
                }
                case JDecl.Method method -> {
                    validateIdentifier(method.name(), "method");
                    String signature = method.name() + method.parameters().stream()
                            .map(parameter -> parameter.type().toString())
                            .reduce("", (left, right) -> left + "," + right);
                    require(methods.add(signature), "duplicate method signature '" + method.name()
                            + "' in " + type.name());
                    validateTypeReference(method.returnType(), "method " + method.name());
                    for (JDecl.Parameter parameter : method.parameters()) {
                        validateParameter(parameter, "method " + method.name());
                    }
                    if (method.body() == null) {
                        require(type.kind() == JDecl.Kind.INTERFACE
                                        || method.modifiers().contains(JDecl.Modifier.ABSTRACT),
                                "method " + method.name() + " of " + type.name()
                                        + " has no body but is not abstract");
                    } else {
                        validateStatements(method.body(), "method " + method.name());
                    }
                }
                case JDecl.ClassDecl nested -> {
                    require(nestedTypes.add(nested.name()), "duplicate nested type '"
                            + nested.name() + "' in " + type.name());
                    validateType(nested, packageName);
                }
            }
        }
    }

    private static void validateParameter(JDecl.Parameter parameter, String owner) {
        validateIdentifier(parameter.name(), "parameter");
        validateTypeReference(parameter.type(), "parameter " + parameter.name() + " of " + owner);
    }

    private static void validateTypeReference(JType type, String where) {
        require(type != null, "missing type for " + where);
        switch (type) {
            case JType.Void ignored -> require(where.startsWith("method"),
                    "void is only a method result, not the type of " + where);
            case JType.Primitive ignored -> { }
            case JType.TypeVariable variable -> validateIdentifier(variable.name(), "type variable");
            case JType.Array array -> {
                require(!array.element().isVoid(), "an array cannot hold void: " + where);
                validateTypeReference(array.element(), "array element of " + where);
            }
            case JType.Wildcard wildcard -> {
                if (wildcard.bound() != null) {
                    validateTypeReference(wildcard.bound(), "wildcard bound of " + where);
                }
            }
            case JType.Declared declared -> {
                if (!declared.packageName().isEmpty()) {
                    validatePackage(declared.packageName());
                }
                for (String enclosing : declared.enclosing()) {
                    validateIdentifier(enclosing, "enclosing type");
                }
                validateIdentifier(declared.name(), "declared type");
                List<String> arguments = new ArrayList<>();
                for (JType argument : declared.arguments()) {
                    require(!(argument instanceof JType.Void), "a type argument cannot be void: "
                            + where);
                    require(!(argument instanceof JType.Wildcard wildcard
                                    && wildcard.bound() instanceof JType.Primitive),
                            "a wildcard bound cannot be primitive: " + where);
                    validateTypeReference(argument, "type argument of " + where);
                    arguments.add(argument.toString());
                }
            }
        }
    }

    private static void validateStatements(List<JStmt> statements, String where) {
        for (JStmt statement : statements) {
            validateStatement(statement, where);
        }
    }

    private static void validateStatement(JStmt statement, String where) {
        require(statement != null, "missing statement in " + where);
        switch (statement) {
            case JStmt.Block block -> validateStatements(block.statements(), where);
            case JStmt.VarDecl declaration -> {
                validateIdentifier(declaration.name(), "local variable");
                validateTypeReference(declaration.type(), "local variable " + declaration.name());
            }
            case JStmt.If branch -> {
                validateStatement(branch.thenBranch(), where);
                if (branch.elseBranch() != null) {
                    validateStatement(branch.elseBranch(), where);
                }
            }
            case JStmt.While loop -> validateStatement(loop.body(), where);
            case JStmt.ForEach loop -> {
                validateIdentifier(loop.name(), "loop variable");
                validateTypeReference(loop.elementType(), "loop variable " + loop.name());
                validateStatement(loop.body(), where);
            }
            case JStmt.For loop -> {
                validateStatements(loop.init(), where);
                validateStatements(loop.update(), where);
                validateStatement(loop.body(), where);
            }
            case JStmt.Switch selection -> {
                for (JStmt.SwitchArm arm : selection.arms()) {
                    validateStatements(arm.body(), where);
                }
                if (selection.defaultBody() != null) {
                    validateStatement(selection.defaultBody(), where);
                }
            }
            case JStmt.Try attempt -> {
                validateStatement(attempt.body(), where);
                for (JStmt.Catch catcher : attempt.catches()) {
                    validateIdentifier(catcher.name(), "catch variable");
                    validateStatement(catcher.body(), where);
                }
                if (attempt.finallyBody() != null) {
                    validateStatement(attempt.finallyBody(), where);
                }
            }
            case JStmt.Synchronized guarded -> validateStatement(guarded.body(), where);
            case JStmt.SuperCall ignored -> { }
            case JStmt.ExpressionStatement ignored -> { }
            case JStmt.Return ignored -> { }
            case JStmt.Break ignored -> { }
            case JStmt.Continue ignored -> { }
            case JStmt.Throw ignored -> { }
        }
    }

    private static void validatePackage(String packageName) {
        require(!packageName.isEmpty(), "an empty package name is not valid here");
        for (String part : packageName.split("\\.", -1)) {
            validateIdentifier(part, "package component");
        }
    }

    private static void validateIdentifier(String name, String kind) {
        require(name != null && !name.isEmpty(), "a " + kind + " name is required");
        require(Character.isJavaIdentifierStart(name.charAt(0)),
                "'" + name + "' is not a valid Java " + kind + " name");
        for (int i = 1; i < name.length(); i++) {
            require(Character.isJavaIdentifierPart(name.charAt(i)),
                    "'" + name + "' is not a valid Java " + kind + " name");
        }
        require(!RESERVED.contains(name), "'" + name + "' is a Java keyword, not a " + kind
                + " name");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("invalid Java IR: " + message);
        }
    }
}
