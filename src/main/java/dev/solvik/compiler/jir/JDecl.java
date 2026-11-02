package dev.solvik.compiler.jir;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A Java declaration: a compilation unit, a class or interface, and its members. */
public interface JDecl {

    /** A Java modifier keyword. */
    enum Modifier {
        PUBLIC, PROTECTED, PRIVATE, STATIC, FINAL, ABSTRACT, DEFAULT, SEALED;

        public String keyword() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** An annotation with its arguments. */
    record Annotation(JType.Declared type, List<JExpr> arguments) {
        public Annotation {
            Objects.requireNonNull(type, "annotation type");
            arguments = List.copyOf(arguments);
        }
    }

    /** A formal parameter. */
    record Parameter(JType type, String name) {
        public Parameter {
            Objects.requireNonNull(type, "parameter type");
            Objects.requireNonNull(name, "parameter name");
        }
    }

    /** A member of a class or interface. */
    sealed interface Member permits Field, Constructor, Method, ClassDecl, StaticInitializer {
    }

    /** A field declaration with an optional initializer. */
    record Field(Set<Modifier> modifiers, JType type, String name, JExpr initializer,
                 List<Annotation> annotations) implements Member {
        public Field {
            modifiers = Set.copyOf(modifiers);
            Objects.requireNonNull(type, "field type");
            Objects.requireNonNull(name, "field name");
            annotations = List.copyOf(annotations);
        }

        public static Field of(Set<Modifier> modifiers, JType type, String name) {
            return new Field(modifiers, type, name, null, List.of());
        }

        public static Field of(Set<Modifier> modifiers, JType type, String name,
                JExpr initializer) {
            return new Field(modifiers, type, name, initializer, List.of());
        }
    }

    /**
     * A Java {@code static { ... }} initializer block.
     *
     * <p>Static blocks and static field initializers share one {@code <clinit>} in source order, so
     * this member keeps the position it has in the class member list.
     */
    record StaticInitializer(List<JStmt> body) implements Member {
        public StaticInitializer {
            body = List.copyOf(body);
        }
    }

    /** A constructor; its name is the enclosing class's name and is not stored here. */
    record Constructor(Set<Modifier> modifiers, List<Parameter> parameters, List<JStmt> body,
                       List<Annotation> annotations) implements Member {
        public Constructor {
            modifiers = Set.copyOf(modifiers);
            parameters = List.copyOf(parameters);
            body = List.copyOf(body);
            annotations = List.copyOf(annotations);
        }

        public static Constructor of(Set<Modifier> modifiers, List<Parameter> parameters,
                List<JStmt> body) {
            return new Constructor(modifiers, parameters, body, List.of());
        }
    }

    /**
     * A method.
     *
     * @param body the body statements, or {@code null} for an abstract or interface method without
     *             a body
     */
    record Method(Set<Modifier> modifiers, List<String> typeParameters, JType returnType,
                  String name, List<Parameter> parameters, List<JStmt> body,
                  List<Annotation> annotations) implements Member {
        public Method {
            modifiers = Set.copyOf(modifiers);
            typeParameters = List.copyOf(typeParameters);
            Objects.requireNonNull(returnType, "return type");
            Objects.requireNonNull(name, "method name");
            parameters = List.copyOf(parameters);
            annotations = List.copyOf(annotations);
            if (body != null && body.isEmpty() && returnType != JTypes.VOID) {
                throw new IllegalArgumentException("method " + name + " returns " + returnType
                        + " but has an empty body");
            }
        }

        public static Method of(Set<Modifier> modifiers, JType returnType, String name,
                List<Parameter> parameters, List<JStmt> body) {
            return new Method(modifiers, List.of(), returnType, name, parameters, body, List.of());
        }
    }

    /** Whether a class declaration creates values or declares an interface contract. */
    enum Kind {
        CLASS, INTERFACE
    }

    /**
     * A class or interface declaration.
     *
     * @param interfaces the types this class implements or this interface extends
     * @param members    fields, constructors, methods, and nested classes in declaration order
     */
    record ClassDecl(Kind kind, Set<Modifier> modifiers, String name, List<String> typeParameters,
                     JType superClass, List<JType> interfaces, List<Member> members,
                     List<Annotation> annotations) implements Member {
        public ClassDecl {
            Objects.requireNonNull(kind, "kind");
            modifiers = Set.copyOf(modifiers);
            Objects.requireNonNull(name, "type name");
            typeParameters = List.copyOf(typeParameters);
            interfaces = List.copyOf(interfaces);
            members = List.copyOf(members);
            annotations = List.copyOf(annotations);
            for (Member member : members) {
                if (kind == Kind.INTERFACE && member instanceof Constructor) {
                    throw new IllegalArgumentException("an interface " + name + " cannot declare a constructor");
                }
            }
        }

        public static ClassDecl classOf(String name) {
            return new ClassDecl(Kind.CLASS, EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), name,
                    List.of(), null, List.of(), List.of(), List.of());
        }

        /** This declaration with additional members appended. */
        public ClassDecl withMembers(List<Member> additional) {
            List<Member> combined = new java.util.ArrayList<>(members);
            combined.addAll(additional);
            return new ClassDecl(kind, modifiers, name, typeParameters, superClass, interfaces,
                    combined, annotations);
        }
    }

    /**
     * A compilation unit: one Java package and its generated top-level types.
     *
     * @param packageName the package name, never empty for generated code
     * @param types       the top-level types, in emission order
     */
    record CompilationUnit(String packageName, List<ClassDecl> types) {
        public CompilationUnit {
            Objects.requireNonNull(packageName, "package name");
            types = List.copyOf(types);
            if (types.isEmpty()) {
                throw new IllegalArgumentException("a compilation unit needs at least one type");
            }
        }

        /** The single type this unit declares, which is the file's public type. */
        public ClassDecl primary() {
            return types.getFirst();
        }
    }
}
