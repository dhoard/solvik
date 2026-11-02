package dev.solvik.compiler.jir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders a typed Java IR compilation unit as deterministic Java 25 source.
 *
 * <p>The renderer knows nothing about Solvik: it takes declarations, expressions, and statements
 * and produces text. It owns indentation, braces, literal escaping, import selection, names, and
 * every parenthesis, which it derives from operator precedence rather than from caller-supplied
 * grouping.
 */
public final class JavaSourceRenderer {

    private static final String INDENT = "    ";

    /** Renders one compilation unit, including its package statement and imports. */
    public String render(JDecl.CompilationUnit unit) {
        JavaIrValidator.validate(unit);
        return new UnitRenderer(unit).render();
    }

    /** One per-unit renderer: import decisions and the output buffer are unit-local. */
    private static final class UnitRenderer {
        private final JDecl.CompilationUnit unit;
        private final Set<String> imports;
        private final Map<String, Integer> simpleNameCounts;
        private final Set<String> declaredTypeNames;
        private final StringBuilder out = new StringBuilder();
        private int depth;

        UnitRenderer(JDecl.CompilationUnit unit) {
            this.unit = unit;
            Set<String> referenced = new LinkedHashSet<>();
            Set<String> declared = new LinkedHashSet<>();
            for (JDecl.ClassDecl type : unit.types()) {
                collectDeclaredNames(type, declared);
                collectType(type.superClass(), referenced);
                type.interfaces().forEach(implemented -> collectType(implemented, referenced));
                for (JDecl.Annotation annotation : type.annotations()) {
                    collectType(annotation.type(), referenced);
                }
                for (JDecl.Member member : type.members()) {
                    collectMember(member, referenced, declared);
                }
            }
            Map<String, Set<String>> bySimpleName = new LinkedHashMap<>();
            for (String fqn : referenced) {
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                bySimpleName.computeIfAbsent(simple, ignored -> new LinkedHashSet<>()).add(fqn);
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            bySimpleName.forEach((simple, names) -> counts.put(simple, names.size()));
            this.simpleNameCounts = counts;
            this.declaredTypeNames = declared;
            Set<String> chosen = new TreeSet<>();
            for (String fqn : referenced) {
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                String packageName = fqn.substring(0, fqn.lastIndexOf('.'));
                if (packageName.equals(unit.packageName()) || packageName.equals("java.lang")) {
                    continue;
                }
                if (counts.getOrDefault(simple, 0) > 1 || declared.contains(simple)) {
                    continue;
                }
                chosen.add(fqn);
            }
            this.imports = chosen;
        }

        private void collectMember(JDecl.Member member, Set<String> referenced,
                Set<String> declared) {
            switch (member) {
                case JDecl.Field field -> {
                    collectType(field.type(), referenced);
                    collectExpr(field.initializer(), referenced);
                    field.annotations().forEach(a -> collectType(a.type(), referenced));
                }
                case JDecl.Constructor constructor -> {
                    constructor.parameters().forEach(p -> collectType(p.type(), referenced));
                    collectStatements(constructor.body(), referenced);
                    constructor.annotations().forEach(a -> collectType(a.type(), referenced));
                }
                case JDecl.StaticInitializer initializer ->
                        collectStatements(initializer.body(), referenced);
                case JDecl.Method method -> {
                    collectType(method.returnType(), referenced);
                    method.parameters().forEach(p -> collectType(p.type(), referenced));
                    collectStatements(method.body(), referenced);
                    method.annotations().forEach(a -> collectType(a.type(), referenced));
                }
                case JDecl.ClassDecl nested -> {
                    collectDeclaredNames(nested, declared);
                    collectType(nested.superClass(), referenced);
                    nested.interfaces().forEach(t -> collectType(t, referenced));
                    nested.annotations().forEach(a -> collectType(a.type(), referenced));
                    for (JDecl.Member child : nested.members()) {
                        collectMember(child, referenced, declared);
                    }
                }
            }
        }

        private static void collectDeclaredNames(JDecl.ClassDecl type, Set<String> declared) {
            declared.add(type.name());
            for (JDecl.Member member : type.members()) {
                if (member instanceof JDecl.ClassDecl nested) {
                    collectDeclaredNames(nested, declared);
                }
            }
        }

        private static void collectType(JType type, Set<String> referenced) {
            switch (type) {
                case null -> { }
                case JType.Declared declared -> {
                    String top = declared.packageName().isEmpty() ? declared.topLevelName()
                            : declared.packageName() + "." + declared.topLevelName();
                    referenced.add(top);
                    declared.arguments().forEach(argument -> collectType(argument, referenced));
                }
                case JType.Array array -> collectType(array.element(), referenced);
                case JType.Wildcard wildcard -> collectType(wildcard.bound(), referenced);
                default -> { }
            }
        }

        private static void collectStatements(List<JStmt> statements, Set<String> referenced) {
            if (statements == null) {
                return;
            }
            for (JStmt statement : statements) {
                collectStatement(statement, referenced);
            }
        }

        private static void collectStatement(JStmt statement, Set<String> referenced) {
            switch (statement) {
                case JStmt.Block block -> collectStatements(block.statements(), referenced);
                case JStmt.VarDecl declaration -> {
                    collectType(declaration.type(), referenced);
                    collectExpr(declaration.initializer(), referenced);
                    declaration.annotations().forEach(a -> collectType(a.type(), referenced));
                }
                case JStmt.ExpressionStatement expression ->
                        collectExpr(expression.expression(), referenced);
                case JStmt.If branch -> {
                    collectExpr(branch.condition(), referenced);
                    collectStatement(branch.thenBranch(), referenced);
                    if (branch.elseBranch() != null) {
                        collectStatement(branch.elseBranch(), referenced);
                    }
                }
                case JStmt.While loop -> {
                    collectExpr(loop.condition(), referenced);
                    collectStatement(loop.body(), referenced);
                }
                case JStmt.ForEach loop -> {
                    collectType(loop.elementType(), referenced);
                    collectExpr(loop.iterable(), referenced);
                    collectStatement(loop.body(), referenced);
                }
                case JStmt.For loop -> {
                    collectStatements(loop.init(), referenced);
                    collectExpr(loop.condition(), referenced);
                    collectStatements(loop.update(), referenced);
                    collectStatement(loop.body(), referenced);
                }
                case JStmt.Switch selection -> {
                    collectExpr(selection.selector(), referenced);
                    for (JStmt.SwitchArm arm : selection.arms()) {
                        collectExpr(arm.label(), referenced);
                        collectType(arm.patternType(), referenced);
                        collectStatements(arm.body(), referenced);
                    }
                    if (selection.defaultBody() != null) {
                        collectStatement(selection.defaultBody(), referenced);
                    }
                }
                case JStmt.Return returned -> collectExpr(returned.value(), referenced);
                case JStmt.SuperCall call -> call.arguments().forEach(
                        argument -> collectExpr(argument, referenced));
                case JStmt.Throw thrown -> collectExpr(thrown.exception(), referenced);
                case JStmt.Try attempt -> {
                    collectStatement(attempt.body(), referenced);
                    for (JStmt.Catch catcher : attempt.catches()) {
                        collectType(catcher.type(), referenced);
                        collectStatement(catcher.body(), referenced);
                    }
                    if (attempt.finallyBody() != null) {
                        collectStatement(attempt.finallyBody(), referenced);
                    }
                }
                case JStmt.Break ignored -> { }
                case JStmt.Continue ignored -> { }
            }
        }

        private static void collectExpr(JExpr expression, Set<String> referenced) {
            switch (expression) {
                case null -> { }
                case JExpr.IntLiteral ignored -> { }
                case JExpr.FloatingLiteral ignored -> { }
                case JExpr.StringLiteral ignored -> { }
                case JExpr.BooleanLiteral ignored -> { }
                case JExpr.NullLiteral ignored -> { }
                case JExpr.Name ignored -> { }
                case JExpr.FieldAccess access -> collectExpr(access.target(), referenced);
                case JExpr.StaticField field -> collectType(field.owner(), referenced);
                case JExpr.Call call -> {
                    collectExpr(call.receiver(), referenced);
                    if (call.receiver() == null) {
                        collectType(call.owner(), referenced);
                    }
                    call.typeArguments().forEach(t -> collectType(t, referenced));
                    call.arguments().forEach(a -> collectExpr(a, referenced));
                }
                case JExpr.New created -> {
                    collectType(created.created(), referenced);
                    created.arguments().forEach(a -> collectExpr(a, referenced));
                }
                case JExpr.NewArray array -> {
                    collectType(array.elementType(), referenced);
                    array.elements().forEach(e -> collectExpr(e, referenced));
                }
                case JExpr.ArrayAccess access -> {
                    collectExpr(access.array(), referenced);
                    collectExpr(access.index(), referenced);
                }
                case JExpr.Binary binary -> {
                    collectExpr(binary.left(), referenced);
                    collectExpr(binary.right(), referenced);
                }
                case JExpr.Unary unary -> collectExpr(unary.operand(), referenced);
                case JExpr.Cast cast -> {
                    collectType(cast.target(), referenced);
                    collectExpr(cast.operand(), referenced);
                }
                case JExpr.Conditional conditional -> {
                    collectExpr(conditional.condition(), referenced);
                    collectExpr(conditional.whenTrue(), referenced);
                    collectExpr(conditional.whenFalse(), referenced);
                }
                case JExpr.Assignment assignment -> {
                    collectExpr(assignment.target(), referenced);
                    collectExpr(assignment.value(), referenced);
                }
                case JExpr.InstanceOf test -> {
                    collectExpr(test.operand(), referenced);
                    collectType(test.tested(), referenced);
                }
            }
        }

        String render() {
            out.append("package ").append(unit.packageName()).append(";\n\n");
            if (!imports.isEmpty()) {
                for (String imported : imports) {
                    out.append("import ").append(imported).append(";\n");
                }
                out.append('\n');
            }
            boolean first = true;
            for (JDecl.ClassDecl type : unit.types()) {
                if (!first) {
                    out.append('\n');
                }
                renderClass(type);
                first = false;
            }
            return out.toString();
        }

        // ------------------------------------------------------------- declarations

        private void renderClass(JDecl.ClassDecl type) {
            renderAnnotations(type.annotations());
            appendIndent();
            out.append(modifiers(type.modifiers()));
            out.append(type.kind() == JDecl.Kind.INTERFACE ? "interface " : "class ");
            out.append(type.name());
            if (!type.typeParameters().isEmpty()) {
                out.append('<').append(String.join(", ", type.typeParameters())).append('>');
            }
            if (type.superClass() != null) {
                out.append(" extends ").append(typeName(type.superClass()));
            }
            if (!type.interfaces().isEmpty()) {
                out.append(type.kind() == JDecl.Kind.INTERFACE ? " extends " : " implements ");
                out.append(joinTypes(type.interfaces()));
            }
            out.append(" {\n");
            depth++;
            renderMembers(type);
            depth--;
            appendIndent();
            out.append("}\n");
        }

        private void renderMembers(JDecl.ClassDecl type) {
            boolean previousWasField = false;
            boolean first = true;
            for (JDecl.Member member : type.members()) {
                boolean isField = member instanceof JDecl.Field
                        || member instanceof JDecl.StaticInitializer;
                if (!first && !(isField && previousWasField)) {
                    out.append('\n');
                }
                switch (member) {
                    case JDecl.Field field -> renderField(field);
                    case JDecl.Constructor constructor -> renderConstructor(type, constructor);
                    case JDecl.StaticInitializer initializer -> renderStaticInitializer(initializer);
                    case JDecl.Method method -> renderMethod(method);
                    case JDecl.ClassDecl nested -> renderClass(nested);
                }
                previousWasField = isField;
                first = false;
            }
        }

        private void renderField(JDecl.Field field) {
            renderAnnotations(field.annotations());
            appendIndent();
            out.append(modifiers(field.modifiers()));
            out.append(typeName(field.type())).append(' ').append(field.name());
            if (field.initializer() != null) {
                out.append(" = ");
                renderExpr(field.initializer(), PREC_ASSIGN);
            }
            out.append(";\n");
        }

        private void renderStaticInitializer(JDecl.StaticInitializer initializer) {
            appendIndent();
            out.append("static ");
            renderBlock(initializer.body());
        }

        private void renderConstructor(JDecl.ClassDecl owner, JDecl.Constructor constructor) {
            renderAnnotations(constructor.annotations());
            appendIndent();
            out.append(modifiers(constructor.modifiers()));
            out.append(owner.name()).append('(').append(parameters(constructor.parameters()))
                    .append(") ");
            renderBlock(constructor.body());
        }

        private void renderMethod(JDecl.Method method) {
            renderAnnotations(method.annotations());
            appendIndent();
            out.append(modifiers(method.modifiers()));
            if (!method.typeParameters().isEmpty()) {
                out.append('<').append(String.join(", ", method.typeParameters())).append("> ");
            }
            out.append(typeName(method.returnType())).append(' ').append(method.name())
                    .append('(').append(parameters(method.parameters())).append(')');
            if (method.body() == null) {
                out.append(";\n");
                return;
            }
            out.append(' ');
            renderBlock(method.body());
        }

        private void renderAnnotations(List<JDecl.Annotation> annotations) {
            for (JDecl.Annotation annotation : annotations) {
                appendIndent();
                out.append('@').append(typeName(annotation.type()));
                if (!annotation.arguments().isEmpty()) {
                    out.append('(');
                    for (int i = 0; i < annotation.arguments().size(); i++) {
                        if (i != 0) {
                            out.append(", ");
                        }
                        renderExpr(annotation.arguments().get(i), PREC_ASSIGN);
                    }
                    out.append(')');
                }
                out.append('\n');
            }
        }

        private String parameters(List<JDecl.Parameter> parameters) {
            List<String> rendered = new ArrayList<>();
            for (JDecl.Parameter parameter : parameters) {
                rendered.add(typeName(parameter.type()) + " " + parameter.name());
            }
            return String.join(", ", rendered);
        }

        private static String modifiers(Set<JDecl.Modifier> modifiers) {
            if (modifiers.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (JDecl.Modifier modifier : JDecl.Modifier.values()) {
                if (modifiers.contains(modifier)) {
                    text.append(modifier.keyword()).append(' ');
                }
            }
            return text.toString();
        }

        private String joinTypes(List<JType> types) {
            List<String> rendered = new ArrayList<>();
            for (JType type : types) {
                rendered.add(typeName(type));
            }
            return String.join(", ", rendered);
        }

        // -------------------------------------------------------------- statements

        private void renderBlock(List<JStmt> statements) {
            out.append("{\n");
            depth++;
            for (JStmt statement : statements) {
                renderStatement(statement);
            }
            depth--;
            appendIndent();
            out.append("}\n");
        }

        private void renderStatement(JStmt statement) {
            switch (statement) {
                case JStmt.Block block -> {
                    appendIndent();
                    renderBlock(block.statements());
                }
                case JStmt.VarDecl declaration -> {
                    for (JDecl.Annotation annotation : declaration.annotations()) {
                        appendIndent();
                        out.append('@').append(typeName(annotation.type()));
                        if (!annotation.arguments().isEmpty()) {
                            out.append('(');
                            for (int i = 0; i < annotation.arguments().size(); i++) {
                                if (i != 0) {
                                    out.append(", ");
                                }
                                renderExpr(annotation.arguments().get(i), PREC_ASSIGN);
                            }
                            out.append(')');
                        }
                        out.append('\n');
                    }
                    appendIndent();
                    if (declaration.isFinal()) {
                        out.append("final ");
                    }
                    out.append(typeName(declaration.type())).append(' ').append(declaration.name());
                    if (declaration.initializer() != null) {
                        out.append(" = ");
                        renderExpr(declaration.initializer(), PREC_ASSIGN);
                    }
                    out.append(";\n");
                }
                case JStmt.ExpressionStatement expression -> {
                    appendIndent();
                    renderExpr(expression.expression(), PREC_ASSIGN);
                    out.append(";\n");
                }
                case JStmt.If branch -> renderIf(branch);
                case JStmt.While loop -> {
                    appendIndent();
                    appendLabel(loop.label());
                    out.append("while (");
                    renderExpr(loop.condition(), PREC_ASSIGN);
                    out.append(") ");
                    renderBody(loop.body());
                }
                case JStmt.ForEach loop -> {
                    appendIndent();
                    appendLabel(loop.label());
                    out.append("for (").append(typeName(loop.elementType())).append(' ')
                            .append(loop.name()).append(" : ");
                    renderExpr(loop.iterable(), PREC_ASSIGN);
                    out.append(") ");
                    renderBody(loop.body());
                }
                case JStmt.For loop -> {
                    appendIndent();
                    appendLabel(loop.label());
                    out.append("for (");
                    renderForPart(loop.init());
                    out.append("; ");
                    renderExpr(loop.condition(), PREC_ASSIGN);
                    out.append("; ");
                    renderForPart(loop.update());
                    out.append(") ");
                    renderBody(loop.body());
                }
                case JStmt.Switch selection -> renderSwitch(selection);
                case JStmt.Return returned -> {
                    appendIndent();
                    if (returned.value() == null) {
                        out.append("return;\n");
                    } else {
                        out.append("return ");
                        renderExpr(returned.value(), PREC_ASSIGN);
                        out.append(";\n");
                    }
                }
                case JStmt.Break broken -> {
                    appendIndent();
                    out.append("break");
                    if (broken.label() != null) {
                        out.append(' ').append(broken.label());
                    }
                    out.append(";\n");
                }
                case JStmt.Continue next -> {
                    appendIndent();
                    out.append("continue");
                    if (next.label() != null) {
                        out.append(' ').append(next.label());
                    }
                    out.append(";\n");
                }
                case JStmt.SuperCall call -> {
                    appendIndent();
                    out.append("super(");
                    renderArguments(call.arguments());
                    out.append(");\n");
                }
                case JStmt.Throw thrown -> {
                    appendIndent();
                    out.append("throw ");
                    renderExpr(thrown.exception(), PREC_ASSIGN);
                    out.append(";\n");
                }
                case JStmt.Try attempt -> renderTry(attempt);
            }
        }

        private void renderIf(JStmt.If branch) {
            appendIndent();
            out.append("if (");
            renderExpr(branch.condition(), PREC_ASSIGN);
            out.append(") ");
            renderBody(branch.thenBranch());
            JStmt current = branch.elseBranch();
            while (current instanceof JStmt.If nested) {
                closeBlockLine();
                out.append(" else if (");
                renderExpr(nested.condition(), PREC_ASSIGN);
                out.append(") ");
                renderBody(nested.thenBranch());
                current = nested.elseBranch();
            }
            if (current != null) {
                closeBlockLine();
                out.append(" else ");
                renderBody(current);
            }
        }

        private void renderSwitch(JStmt.Switch selection) {
            appendIndent();
            out.append("switch (");
            renderExpr(selection.selector(), PREC_ASSIGN);
            out.append(") {\n");
            depth++;
            for (JStmt.SwitchArm arm : selection.arms()) {
                appendIndent();
                out.append("case ");
                if (arm.label() != null) {
                    renderExpr(arm.label(), PREC_ASSIGN);
                } else {
                    out.append(typeName(arm.patternType()));
                    if (arm.patternName() != null) {
                        out.append(' ').append(arm.patternName());
                    }
                }
                out.append(" -> ");
                renderBlock(arm.body());
            }
            if (selection.defaultBody() != null) {
                appendIndent();
                out.append("default -> ");
                renderBody(selection.defaultBody());
            }
            depth--;
            appendIndent();
            out.append("}\n");
        }

        private void renderTry(JStmt.Try attempt) {
            appendIndent();
            out.append("try ");
            renderBody(attempt.body());
            for (JStmt.Catch catcher : attempt.catches()) {
                closeBlockLine();
                out.append(" catch (").append(typeName(catcher.type())).append(' ')
                        .append(catcher.name()).append(") ");
                renderBody(catcher.body());
            }
            if (attempt.finallyBody() != null) {
                closeBlockLine();
                out.append(" finally ");
                renderBody(attempt.finallyBody());
            }
        }

        /** Renders a loop or branch body: blocks keep their braces, others get them. */
        private void renderBody(JStmt body) {
            if (body instanceof JStmt.Block block) {
                renderBlock(block.statements());
                return;
            }
            out.append("{\n");
            depth++;
            renderStatement(body);
            depth--;
            appendIndent();
            out.append("}\n");
        }

        private void renderForPart(List<JStmt> statements) {
            boolean first = true;
            for (JStmt statement : statements) {
                if (!first) {
                    out.append(", ");
                }
                switch (statement) {
                    case JStmt.VarDecl declaration -> {
                        out.append(typeName(declaration.type())).append(' ')
                                .append(declaration.name());
                        if (declaration.initializer() != null) {
                            out.append(" = ");
                            renderExpr(declaration.initializer(), PREC_ASSIGN);
                        }
                    }
                    case JStmt.ExpressionStatement expression ->
                            renderExpr(expression.expression(), PREC_ASSIGN);
                    default -> throw new IllegalStateException(
                            "a for-loop header holds declarations and expressions only");
                }
                first = false;
            }
        }

        /** Moves a following {@code else} or {@code catch} onto the closing-brace line. */
        private void closeBlockLine() {
            if (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
                out.setLength(out.length() - 1);
            }
        }

        private void appendLabel(String label) {
            if (label != null) {
                out.append(label).append(": ");
            }
        }

        // ------------------------------------------------------------- expressions

        private static final int PREC_PRIMARY = 15;
        private static final int PREC_UNARY = 14;
        private static final int PREC_MULTIPLICATIVE = 13;
        private static final int PREC_ADDITIVE = 12;
        private static final int PREC_RELATIONAL = 10;
        private static final int PREC_EQUALITY = 9;
        private static final int PREC_AND = 5;
        private static final int PREC_OR = 4;
        private static final int PREC_CONDITIONAL = 3;
        private static final int PREC_ASSIGN = 2;

        private void renderExpr(JExpr expression, int minimumPrecedence) {
            boolean parenthesize = precedence(expression) < minimumPrecedence;
            if (parenthesize) {
                out.append('(');
            }
            switch (expression) {
                case JExpr.IntLiteral literal -> out.append(literal.digits())
                        .append(literal.type() == JType.Primitive.LONG ? "L" : "");
                case JExpr.FloatingLiteral literal -> out.append(literal.digits())
                        .append(literal.type() == JType.Primitive.FLOAT ? "f" : "d");
                case JExpr.StringLiteral literal -> appendQuoted(literal.value());
                case JExpr.BooleanLiteral literal -> out.append(literal.value());
                case JExpr.NullLiteral ignored -> out.append("null");
                case JExpr.Name name -> out.append(name.name());
                case JExpr.FieldAccess access -> {
                    renderExpr(access.target(), PREC_PRIMARY);
                    out.append('.').append(access.name());
                }
                case JExpr.StaticField field -> {
                    out.append(typeName(field.owner())).append('.').append(field.name());
                }
                case JExpr.Call call -> {
                    if (call.receiver() != null) {
                        renderExpr(call.receiver(), PREC_PRIMARY);
                        out.append('.');
                    } else {
                        out.append(typeName(call.owner())).append('.');
                    }
                    if (!call.typeArguments().isEmpty()) {
                        out.append('<');
                        List<String> arguments = new ArrayList<>();
                        for (JType argument : call.typeArguments()) {
                            arguments.add(typeName(argument));
                        }
                        out.append(String.join(", ", arguments)).append('>');
                    }
                    out.append(call.name()).append('(');
                    renderArguments(call.arguments());
                    out.append(')');
                }
                case JExpr.New created -> {
                    out.append("new ").append(typeName(created.created())).append('(');
                    renderArguments(created.arguments());
                    out.append(')');
                }
                case JExpr.NewArray array -> {
                    out.append("new ").append(typeName(array.elementType())).append("[] {");
                    if (!array.elements().isEmpty()) {
                        out.append(' ');
                        renderArguments(array.elements());
                        out.append(' ');
                    }
                    out.append('}');
                }
                case JExpr.ArrayAccess access -> {
                    renderExpr(access.array(), PREC_PRIMARY);
                    out.append('[');
                    renderExpr(access.index(), PREC_ASSIGN);
                    out.append(']');
                }
                case JExpr.Binary binary -> {
                    int operatorPrecedence = precedence(binary.operator());
                    renderExpr(binary.left(), operatorPrecedence);
                    out.append(' ').append(binary.operator().symbol()).append(' ');
                    renderExpr(binary.right(), operatorPrecedence + 1);
                }
                case JExpr.Unary unary -> {
                    out.append(unary.operator().symbol());
                    renderExpr(unary.operand(),
                            JExpr.UnaryOp.INCREMENT == unary.operator()
                                    || JExpr.UnaryOp.DECREMENT == unary.operator()
                                    ? PREC_PRIMARY : PREC_UNARY + 1);
                }
                case JExpr.Cast cast -> {
                    out.append('(').append(typeName(cast.target())).append(") ");
                    // A reference cast takes a UnaryExpressionNotPlusMinus (JLS 15.16), so a
                    // signed operand needs its own parentheses: `(Object) -1` is not a cast.
                    renderExpr(cast.operand(),
                            castOperandNeedsParentheses(cast) ? PREC_UNARY + 1 : PREC_UNARY);
                }
                case JExpr.Conditional conditional -> {
                    renderExpr(conditional.condition(), PREC_OR + 1);
                    out.append(" ? ");
                    renderExpr(conditional.whenTrue(), PREC_ASSIGN);
                    out.append(" : ");
                    renderExpr(conditional.whenFalse(), PREC_CONDITIONAL);
                }
                case JExpr.Assignment assignment -> {
                    renderExpr(assignment.target(), PREC_PRIMARY);
                    out.append(" = ");
                    renderExpr(assignment.value(), PREC_ASSIGN);
                }
                case JExpr.InstanceOf test -> {
                    renderExpr(test.operand(), PREC_RELATIONAL);
                    out.append(" instanceof ").append(typeName(test.tested()));
                    if (test.patternName() != null) {
                        out.append(' ').append(test.patternName());
                    }
                }
            }
            if (parenthesize) {
                out.append(')');
            }
        }

        /** Whether a cast's operand must be parenthesized to keep the cast a cast (JLS 15.16). */
        private static boolean castOperandNeedsParentheses(JExpr.Cast cast) {
            return !cast.target().isPrimitive()
                    && cast.operand() instanceof JExpr.Unary unary
                    && switch (unary.operator()) {
                        case NEGATE, INCREMENT, DECREMENT -> true;
                        case NOT -> false;
                    };
        }

        private void renderArguments(List<JExpr> arguments) {
            for (int i = 0; i < arguments.size(); i++) {
                if (i != 0) {
                    out.append(", ");
                }
                renderExpr(arguments.get(i), PREC_ASSIGN);
            }
        }

        private static int precedence(JExpr expression) {
            return switch (expression) {
                case JExpr.Binary binary -> precedence(binary.operator());
                case JExpr.Unary ignored -> PREC_UNARY;
                case JExpr.Cast ignored -> PREC_UNARY;
                case JExpr.Conditional ignored -> PREC_CONDITIONAL;
                case JExpr.Assignment ignored -> PREC_ASSIGN;
                case JExpr.InstanceOf ignored -> PREC_RELATIONAL;
                default -> PREC_PRIMARY;
            };
        }

        private static int precedence(JExpr.BinaryOp operator) {
            return switch (operator) {
                case MULTIPLY, DIVIDE, REMAINDER -> PREC_MULTIPLICATIVE;
                case PLUS, MINUS -> PREC_ADDITIVE;
                case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> PREC_RELATIONAL;
                case EQUAL, NOT_EQUAL -> PREC_EQUALITY;
                case AND -> PREC_AND;
                case OR -> PREC_OR;
            };
        }

        // ------------------------------------------------------------------ types

        private String typeName(JType type) {
            return switch (type) {
                case null -> throw new IllegalStateException("a Java type is required");
                case JType.Primitive primitive -> primitive.keyword();
                case JType.Void ignored -> "void";
                case JType.Array array -> typeName(array.element()) + "[]";
                case JType.TypeVariable variable -> variable.name();
                case JType.Wildcard wildcard -> wildcard.bound() == null ? "?"
                        : "? " + (wildcard.lower() ? "super " : "extends ")
                                + typeName(wildcard.bound());
                case JType.Declared declared -> declaredName(declared);
            };
        }

        private String declaredName(JType.Declared type) {
            StringBuilder name = new StringBuilder();
            if (type.packageName().equals(unit.packageName())) {
                name.append(localName(type));
            } else if (type.packageName().equals("java.lang") && type.enclosing().isEmpty()
                    && simpleNameCounts.getOrDefault(type.name(), 0) == 1
                    && !declaredTypeNames.contains(type.name())) {
                name.append(type.name());
            } else {
                String top = topLevelFqn(type);
                if (imports.contains(top)) {
                    name.append(localName(type));
                } else {
                    name.append(type.binaryName());
                }
            }
            if (!type.arguments().isEmpty()) {
                name.append('<');
                List<String> arguments = new ArrayList<>();
                for (JType argument : type.arguments()) {
                    arguments.add(typeName(argument));
                }
                name.append(String.join(", ", arguments)).append('>');
            }
            return name.toString();
        }

        private static String localName(JType.Declared type) {
            if (type.enclosing().isEmpty()) {
                return type.name();
            }
            return String.join(".", type.enclosing()) + "." + type.name();
        }

        private static String topLevelFqn(JType.Declared type) {
            String prefix = type.packageName().isEmpty() ? "" : type.packageName() + ".";
            return prefix + type.topLevelName();
        }

        // ----------------------------------------------------------------- helpers

        private void appendQuoted(String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                switch (character) {
                    case '\\' -> out.append("\\\\");
                    case '"' -> out.append("\\\"");
                    case '\n' -> out.append("\\n");
                    case '\t' -> out.append("\\t");
                    case '\r' -> out.append("\\r");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    default -> {
                        if (character < 0x20) {
                            out.append(String.format(java.util.Locale.ROOT, "\\%03o",
                                    (int) character));
                        } else {
                            out.append(character);
                        }
                    }
                }
            }
            out.append('"');
        }

        private void appendIndent() {
            out.append(INDENT.repeat(depth));
        }
    }
}
