package dev.solvik.compiler.transpile;

import dev.solvik.compiler.jir.JDecl;
import dev.solvik.compiler.jir.JExpr;
import dev.solvik.compiler.jir.JStmt;
import dev.solvik.compiler.jir.JType;
import dev.solvik.compiler.jir.JTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiler-owned, JDK-only support members, declared as structured Java IR.
 *
 * <p>Each entry is a named group of declarations. Only the groups a program reaches are added to
 * the generated program class, plus the groups those reach: dependencies are discovered by walking
 * the IR of the selected members, so a call cannot be missed and a group cannot be emitted with a
 * dependency it does not declare. There is no runtime jar and no source-text injection.
 */
public final class GeneratedIntrinsics {

    private static final JType.Declared BIG_INTEGER = JTypes.BIG_INTEGER;
    private static final JType.Declared BIG_DECIMAL = JTypes.BIG_DECIMAL;
    private static final JType.Declared OBJECT = JTypes.OBJECT;
    private static final JType.Declared STRING = JTypes.STRING;
    private static final JType.Declared MATCHER = JType.Declared.of("java.util.regex", "Matcher");
    private static final JType.Declared COLLECTIONS = JType.Declared.of("java.util", "Collections");
    private static final JType.Declared ARITHMETIC_EXCEPTION =
            JType.Declared.of("java.lang", "ArithmeticException");
    private static final JType.Declared NUMBER_FORMAT_EXCEPTION =
            JType.Declared.of("java.lang", "NumberFormatException");
    private static final JType.Declared ERROR = JTypes.programNested(GeneratedNames.ERROR_CLASS);
    private static final JType.Declared EXIT = JTypes.programNested(GeneratedNames.EXIT_CLASS);
    private static final JType.Declared EXCEPTION =
            JTypes.programNested(GeneratedNames.EXCEPTION_CLASS);

    private static final JType BOOLEAN = JType.Primitive.BOOLEAN;
    private static final JType INT = JType.Primitive.INT;
    private static final JType LONG = JType.Primitive.LONG;
    private static final JType BYTE = JType.Primitive.BYTE;
    private static final JType SHORT = JType.Primitive.SHORT;
    private static final JType FLOAT = JType.Primitive.FLOAT;
    private static final JType DOUBLE = JType.Primitive.DOUBLE;

    private static final Set<JDecl.Modifier> PUBLIC_STATIC =
            Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC);
    private static final Set<JDecl.Modifier> PRIVATE_STATIC =
            Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.STATIC);
    private static final Set<JDecl.Modifier> PUBLIC_INSTANCE = Set.of(JDecl.Modifier.PUBLIC);
    private static final Set<JDecl.Modifier> PRIVATE_INSTANCE = Set.of(JDecl.Modifier.PRIVATE);

    private GeneratedIntrinsics() {
    }

    /**
     * Adds the support members {@code used} reaches to the generated program class.
     *
     * @param program   the program class lowered from the source program
     * @param used      support member names the lowered Java actually calls
     * @param userTypes every declared user type and its type-parameter count, in source order, for
     *                  the generated {@code type()} dispatch
     */
    public static JDecl.ClassDecl addSupport(JDecl.ClassDecl program, Set<String> used,
            Map<String, Integer> userTypes) {
        Map<String, List<JDecl.Member>> registry = registry(userTypes,
                used.contains(GeneratedNames.EXCEPTION_CLASS));
        Set<String> needed = reachable(used, registry);
        List<JDecl.Member> members = new ArrayList<>();
        for (Map.Entry<String, List<JDecl.Member>> entry : registry.entrySet()) {
            if (needed.contains(entry.getKey())) {
                members.addAll(entry.getValue());
            }
        }
        return program.withMembers(members);
    }

    /**
     * The transitive closure of the recorded support uses, discovered from the IR of each group.
     *
     * <p>Dependencies are not declared by hand: every call to the program class and every
     * reference to one of its nested types in a selected group is a dependency. An unknown name
     * fails here rather than emitting Java that does not compile.
     */
    static Set<String> reachable(Set<String> used, Map<String, List<JDecl.Member>> registry) {
        Set<String> needed = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(used);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            List<JDecl.Member> members = registry.get(name);
            if (members == null) {
                throw new IllegalStateException("emitted Java uses unknown support member " + name);
            }
            if (needed.add(name)) {
                Set<String> dependencies = new LinkedHashSet<>();
                collectDependencies(members, dependencies);
                pending.addAll(dependencies);
            }
        }
        return needed;
    }

    /** Builds the registry for one program that emits no exception support. */
    static Map<String, List<JDecl.Member>> registryFor(Map<String, Integer> userTypes) {
        return registry(userTypes, false);
    }

    /**
     * The reachable support groups, in declaration order.
     *
     * @param userTypes     every declared user type and its type-parameter count, in source order
     * @param usesException whether the program emits the {@code Exception} support class, which
     *                      decides if {@code type()} can name it without pulling it in
     */
    private static Map<String, List<JDecl.Member>> registry(Map<String, Integer> userTypes,
            boolean usesException) {
        Map<String, List<JDecl.Member>> registry = new LinkedHashMap<>();
        registry.put("render", List.of(render()));
        registry.put("concat", List.of(concat()));
        registry.put(GeneratedNames.ERROR_CLASS, List.of(errorClass()));
        registry.put(GeneratedNames.EXIT_CLASS, List.of(exitClass()));
        registry.put(GeneratedNames.EXCEPTION_CLASS, List.of(exceptionClass()));
        registry.put("fail", List.of(fail()));
        registry.put("exit", List.of(exit(), exitWithValue()));
        registry.put("equal", List.of(equal()));
        registry.put("equalIntegral", List.of(equalIntegral()));
        registry.put("equalFloat", List.of(equalFloat()));
        registry.put("equalFloating", List.of(equalFloating()));
        registry.put("identity", List.of(identity()));
        registry.put("isScalar", List.of(isScalar()));
        registry.put("hash", List.of(hash()));
        registry.put("array", List.of(array()));
        registry.put("textSubstring", List.of(textSubstring(), textSubstringRange()));
        registry.put("textIndex", List.of(textIndex()));
        registry.put("textSplit", List.of(textSplit()));
        registry.put("toByte", List.of(toByte()));
        registry.put("toShort", List.of(toShort()));
        registry.put("toInteger", List.of(toInteger()));
        registry.put("toLong", List.of(toLong()));
        registry.put("toBigInteger", List.of(toBigInteger()));
        registry.put("toBigDecimal", List.of(toBigDecimal()));
        registry.put("toFloat", List.of(toFloat()));
        registry.put("toDouble", List.of(toDouble()));
        registry.put("toBoolean", List.of(toBoolean()));
        registry.put("required", List.of(required()));
        registry.put("checked", List.of(checked()));
        registry.put("checkedResult", List.of(checkedResult()));
        registry.put("conversion", List.of(conversion()));
        registry.put("isIntegerText", patternTextMembers("isIntegerText", "INTEGER_TEXT",
                "[+-]?[0-9]+"));
        registry.put("isDecimalText", patternTextMembers("isDecimalText", "DECIMAL_TEXT",
                "[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"));
        registry.put("isFloatingText", List.of(floatingText()));
        registry.put("divide", List.of(divideInt(), divideLong(), divideBigInteger(),
                divideBigDecimal()));
        registry.put("remainder", List.of(remainderInt(), remainderLong(),
                remainderBigInteger()));
        registry.put("type", List.of(type(userTypes, usesException)));
        registry.put("plainText", List.of(plainText()));
        return registry;
    }

    // --------------------------------------------------------------------- members

    private static JDecl.Method render() {
        JExpr value = name("value", OBJECT);
        JType.Declared listOfAny = JTypes.listOfAny();
        JExpr array = name("array", listOfAny);
        JExpr text = name("text", JTypes.STRING_BUILDER);
        List<JStmt> loopBody = new ArrayList<>();
        loopBody.add(ifStatement(notEqual(name("i", INT), intLiteral(0)),
                new JStmt.ExpressionStatement(append(text, ", ")), null));
        loopBody.add(new JStmt.ExpressionStatement(append(text, call(null,
                JTypes.programClass(), "render", STRING,
                call(array, listOfAny, "get", OBJECT, name("i", INT))))));
        List<JStmt> body = new ArrayList<>();
        body.add(ifStatement(isNull(value), new JStmt.Return(str("null")), null));
        body.add(ifStatement(new JExpr.InstanceOf(value, listOfAny, "array"),
                new JStmt.Block(List.of(
                        new JStmt.VarDecl(JTypes.STRING_BUILDER, "text", false,
                                new JExpr.New(JTypes.STRING_BUILDER,
                                        List.of(str("[")), JTypes.STRING_BUILDER), List.of()),
                        new JStmt.For(null,
                                List.of(new JStmt.VarDecl(INT, "i", false, intLiteral(0),
                                        List.of())),
                                new JExpr.Binary(JExpr.BinaryOp.LESS, name("i", INT),
                                        call(array, listOfAny, "size", INT), BOOLEAN),
                                List.of(new JStmt.ExpressionStatement(new JExpr.Unary(
                                        JExpr.UnaryOp.INCREMENT, name("i", INT), INT))),
                                new JStmt.Block(loopBody)),
                        new JStmt.Return(call(call(text, JTypes.STRING_BUILDER, "append",
                                JTypes.STRING_BUILDER, str("]")), JTypes.STRING_BUILDER,
                                "toString", STRING)))), null));
        body.add(ifStatement(new JExpr.InstanceOf(value, BIG_DECIMAL, "decimal"),
                new JStmt.Return(call(name("decimal", BIG_DECIMAL), BIG_DECIMAL,
                        "toPlainString", STRING)), null));
        body.add(new JStmt.Return(call(value, OBJECT, "toString", STRING)));
        return method(PUBLIC_STATIC, STRING, "render", List.of(parameter(OBJECT, "value")), body);
    }

    private static JDecl.Method concat() {
        JExpr left = name("left", OBJECT);
        JExpr right = name("right", OBJECT);
        return method(PUBLIC_STATIC, STRING, "concat",
                List.of(parameter(OBJECT, "left"), parameter(OBJECT, "right")),
                List.of(new JStmt.Return(new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        call(null, JTypes.programClass(), "render", STRING, left),
                        call(null, JTypes.programClass(), "render", STRING, right),
                        STRING))));
    }

    private static JDecl.ClassDecl errorClass() {
        List<JDecl.Member> members = new ArrayList<>();
        members.add(serialVersion());
        members.add(JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL), STRING,
                "code"));
        members.add(new JDecl.Constructor(Set.of(),
                List.of(parameter(STRING, "code"), parameter(STRING, "message")),
                List.of(new JStmt.SuperCall(List.of(name("message", STRING),
                        new JExpr.NullLiteral(), new JExpr.BooleanLiteral(false),
                        new JExpr.BooleanLiteral(false))),
                        new JStmt.ExpressionStatement(assign(field("this", "code", STRING),
                                name("code", STRING), STRING))),
                List.of()));
        members.add(method(PUBLIC_INSTANCE, STRING, "code", List.of(),
                List.of(new JStmt.Return(name("code", STRING)))));
        return nestedClass(GeneratedNames.ERROR_CLASS, RuntimeException(), members);
    }

    private static JDecl.ClassDecl exitClass() {        List<JDecl.Member> members = new ArrayList<>();
        members.add(serialVersion());
        members.add(JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL), INT,
                "code"));
        members.add(new JDecl.Constructor(Set.of(), List.of(parameter(INT, "code")),
                List.of(new JStmt.SuperCall(List.of(
                        new JExpr.Binary(JExpr.BinaryOp.PLUS, str("exit "),
                                name("code", INT), STRING),
                        new JExpr.NullLiteral(), new JExpr.BooleanLiteral(false),
                        new JExpr.BooleanLiteral(false))),
                        new JStmt.ExpressionStatement(assign(field("this", "code", INT),
                                name("code", INT), INT))),
                List.of()));
        members.add(method(PUBLIC_INSTANCE, INT, "code", List.of(),
                List.of(new JStmt.Return(name("code", INT)))));
        return nestedClass(GeneratedNames.EXIT_CLASS, RuntimeException(), members);
    }

    /**
     * The built-in {@code Exception} a Solvik program raises and catches (§7.4).
     *
     * <p>It is unchecked, as the language requires, and carries the {@code Integer} code used for
     * dispatch plus the message Java's own {@code Throwable} stores. A negative code is rejected
     * here rather than by every raise site, so the rule holds for a value raised anywhere.
     */
    private static JDecl.ClassDecl exceptionClass() {
        JType.Declared self = JTypes.programNested(GeneratedNames.EXCEPTION_CLASS);
        JExpr code = name("code", JTypes.INTEGER);
        List<JDecl.Member> members = new ArrayList<>();
        members.add(serialVersion());
        members.add(JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.FINAL),
                JTypes.INTEGER, "code"));
        members.add(new JDecl.Constructor(Set.of(JDecl.Modifier.PUBLIC),
                List.of(parameter(JTypes.INTEGER, "code"), parameter(STRING, "message")),
                List.of(
                        new JStmt.SuperCall(List.of(name("message", STRING),
                                new JExpr.NullLiteral(), new JExpr.BooleanLiteral(false),
                                new JExpr.BooleanLiteral(false))),
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.LESS, code, intLiteral(0),
                                BOOLEAN),
                                new JStmt.Throw(call(null, JTypes.programClass(), "fail",
                                        JTypes.RUNTIME_EXCEPTION, str("SOL-R012"),
                                        new JExpr.Binary(JExpr.BinaryOp.PLUS,
                                                str("an exception code must not be negative, found "),
                                                code, STRING))),
                                null),
                        new JStmt.ExpressionStatement(assign(
                                field("this", "code", JTypes.INTEGER), code, JTypes.INTEGER))),
                List.of()));
        members.add(method(PUBLIC_INSTANCE, JTypes.INTEGER, "getCode", List.of(),
                List.of(new JStmt.Return(code))));
        members.add(method(PUBLIC_INSTANCE, STRING, "toString", List.of(),
                List.of(new JStmt.Return(new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS,
                                new JExpr.Binary(JExpr.BinaryOp.PLUS, str("["), code, STRING),
                                str("] "), STRING),
                        call(name("this", self), self, "getMessage", STRING), STRING)))));
        return nestedClass(GeneratedNames.EXCEPTION_CLASS, RuntimeException(), members);
    }

    private static JDecl.Field serialVersion() {
        return JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.STATIC,
                JDecl.Modifier.FINAL), LONG, "serialVersionUID", longLiteral(1));
    }

    private static JType.Declared RuntimeException() {
        return JTypes.RUNTIME_EXCEPTION;
    }

    private static JDecl.ClassDecl nestedClass(String name, JType superClass,
            List<JDecl.Member> members) {
        return new JDecl.ClassDecl(JDecl.Kind.CLASS,
                Set.of(JDecl.Modifier.PUBLIC, JDecl.Modifier.STATIC, JDecl.Modifier.FINAL), name,
                List.of(), superClass, List.of(), members, List.of());
    }

    private static JDecl.Method fail() {
        JExpr code = name("code", STRING);
        JExpr message = name("message", STRING);
        JExpr text = new JExpr.Binary(JExpr.BinaryOp.PLUS,
                new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS, str("solvik: runtime error ["),
                                code, STRING), str("]: "), STRING), message, STRING);
        return method(PUBLIC_STATIC, JTypes.RUNTIME_EXCEPTION, "fail",
                List.of(parameter(STRING, "code"), parameter(STRING, "message")),
                List.of(
                        new JStmt.ExpressionStatement(call(systemField("err"), JTypes.PRINT_STREAM,
                                "println", JTypes.VOID, text)),
                        new JStmt.ExpressionStatement(call(systemField("err"), JTypes.PRINT_STREAM,
                                "flush", JTypes.VOID)),
                        new JStmt.Return(new JExpr.New(ERROR, List.of(code, message), ERROR))));
    }

    private static JDecl.Method exit() {
        return method(PUBLIC_STATIC, JTypes.VOID, "exit", List.of(),
                List.of(new JStmt.ExpressionStatement(call(null, JTypes.programClass(), "exit",
                        JTypes.VOID, intLiteral(0)))));
    }

    private static JDecl.Method exitWithValue() {
        JExpr value = name("value", OBJECT);
        JExpr code = name("code", BIG_INTEGER);
        JExpr invalid = new JExpr.Binary(JExpr.BinaryOp.OR,
                new JExpr.Binary(JExpr.BinaryOp.LESS,
                        call(code, BIG_INTEGER, "signum", INT), intLiteral(0), BOOLEAN),
                new JExpr.Binary(JExpr.BinaryOp.GREATER,
                        call(code, BIG_INTEGER, "compareTo", INT,
                                call(null, BIG_INTEGER, "valueOf", BIG_INTEGER,
                                        longLiteral(255))),
                        intLiteral(0), BOOLEAN), BOOLEAN);
        JExpr message = new JExpr.Binary(JExpr.BinaryOp.PLUS,
                str("exit code must be 0 to 255, found "), code, STRING);
        return method(PUBLIC_STATIC, JTypes.VOID, "exit", List.of(parameter(OBJECT, "value")),
                List.of(
                        new JStmt.VarDecl(BIG_INTEGER, "code", false,
                                call(null, JTypes.programClass(), "toBigInteger", BIG_INTEGER,
                                        value), List.of()),
                        ifStatement(invalid, new JStmt.Throw(call(null,
                                JTypes.programClass(), "fail", JTypes.RUNTIME_EXCEPTION,
                                str("SOL-R007"), message)), null),
                        new JStmt.Throw(new JExpr.New(EXIT,
                                List.of(call(code, BIG_INTEGER, "intValue", INT)), EXIT))));
    }

    private static JDecl.Method equal() {
        JExpr left = name("left", OBJECT);
        JExpr right = name("right", OBJECT);
        JType listOfAny = JTypes.listOfAny();
        JExpr eitherList = new JExpr.Binary(JExpr.BinaryOp.OR,
                new JExpr.InstanceOf(left, listOfAny, null),
                new JExpr.InstanceOf(right, listOfAny, null), BOOLEAN);
        return method(PUBLIC_STATIC, BOOLEAN, "equal",
                List.of(parameter(OBJECT, "left"), parameter(OBJECT, "right")),
                List.of(
                        ifStatement(eitherList, new JStmt.Return(
                                reference(left, right)), null),
                        ifStatement(isNull(left), new JStmt.Return(isNull(right)), null),
                        new JStmt.Return(call(left, OBJECT, "equals", BOOLEAN, right))));
    }

    private static JDecl.Method equalIntegral() {
        return numericEquality("equalIntegral", "longValue", LONG);
    }

    private static JDecl.Method equalFloat() {
        return numericEquality("equalFloat", "floatValue", FLOAT);
    }

    private static JDecl.Method equalFloating() {
        return numericEquality("equalFloating", "doubleValue", DOUBLE);
    }

    private static JDecl.Method numericEquality(String methodName, String conversion,
            JType primitive) {
        JExpr left = name("left", OBJECT);
        JExpr right = name("right", OBJECT);
        JExpr missing = new JExpr.Binary(JExpr.BinaryOp.OR, isNull(left), isNull(right),
                BOOLEAN);
        JExpr compared = new JExpr.Binary(JExpr.BinaryOp.EQUAL,
                call(cast(JTypes.NUMBER, left), JTypes.NUMBER, conversion, primitive),
                call(cast(JTypes.NUMBER, right), JTypes.NUMBER, conversion, primitive), BOOLEAN);
        return method(PUBLIC_STATIC, BOOLEAN, methodName,
                List.of(parameter(OBJECT, "left"), parameter(OBJECT, "right")),
                List.of(ifStatement(missing, new JStmt.Return(reference(left, right)), null),
                        new JStmt.Return(compared)));
    }

    private static JDecl.Method identity() {
        JExpr left = name("left", OBJECT);
        JExpr right = name("right", OBJECT);
        JExpr scalars = new JExpr.Binary(JExpr.BinaryOp.AND,
                call(null, JTypes.programClass(), "isScalar", BOOLEAN, left),
                call(null, JTypes.programClass(), "isScalar", BOOLEAN, right), BOOLEAN);
        return method(PUBLIC_STATIC, BOOLEAN, "identity",
                List.of(parameter(OBJECT, "left"), parameter(OBJECT, "right")),
                List.of(new JStmt.Return(new JExpr.Conditional(scalars,
                        call(null, JTypes.programClass(), "equal", BOOLEAN, left, right),
                        reference(left, right), BOOLEAN))));
    }

    private static JDecl.Method isScalar() {
        JExpr value = name("value", OBJECT);
        List<JType> scalars = List.of(JTypes.BYTE, JTypes.SHORT, JTypes.INTEGER, JTypes.LONG,
                JTypes.FLOAT, JTypes.DOUBLE, BIG_INTEGER, BIG_DECIMAL, STRING, JTypes.BOOLEAN);
        JExpr test = null;
        for (JType scalar : scalars) {
            JExpr next = new JExpr.InstanceOf(value, scalar, null);
            test = test == null ? next
                    : new JExpr.Binary(JExpr.BinaryOp.OR, test, next, BOOLEAN);
        }
        return method(PRIVATE_STATIC, BOOLEAN, "isScalar", List.of(parameter(OBJECT, "value")),
                List.of(new JStmt.Return(test)));
    }

    private static JDecl.Method hash() {
        JExpr value = name("value", OBJECT);
        return method(PUBLIC_STATIC, JTypes.INTEGER, "hash", List.of(parameter(OBJECT, "value")),
                List.of(new JStmt.Return(new JExpr.Conditional(
                        new JExpr.InstanceOf(value, JTypes.listOfAny(), null),
                        call(null, JTypes.SYSTEM, "identityHashCode", INT, value),
                        call(null, JTypes.OBJECTS, "hashCode", INT, value), JTypes.INTEGER))));
    }

    private static JDecl.Method array() {
        JType.Declared list = JTypes.list(new JType.TypeVariable("T"));
        JType arrayOfObject = new JType.Array(OBJECT);
        JExpr result = name("result", list);
        JExpr valuesName = name("values", arrayOfObject);
        List<JStmt> body = new ArrayList<>();
        body.add(new JStmt.VarDecl(list, "result", false, new JExpr.New(
                JTypes.arrayList(new JType.TypeVariable("T")),
                List.of(call(null, COLLECTIONS, "nCopies", JTypes.list(OBJECT),
                        name("size", INT), new JExpr.NullLiteral())), list), List.of()));
        body.add(new JStmt.For(null,
                List.of(new JStmt.VarDecl(INT, "i", false, intLiteral(0), List.of())),
                new JExpr.Binary(JExpr.BinaryOp.LESS, name("i", INT), length(valuesName), BOOLEAN),
                List.of(new JStmt.ExpressionStatement(new JExpr.Unary(JExpr.UnaryOp.INCREMENT,
                        name("i", INT), INT))),
                new JStmt.Block(List.of(new JStmt.ExpressionStatement(call(result, list, "set",
                        JTypes.VOID, name("i", INT),
                        cast(new JType.TypeVariable("T"), new JExpr.ArrayAccess(valuesName,
                                name("i", INT), OBJECT))))))));
        body.add(new JStmt.Return(result));
        return new JDecl.Method(PUBLIC_STATIC, List.of("T"), list, "array",
                List.of(parameter(INT, "size"), parameter(arrayOfObject, "values")), body,
                List.of(suppression("unchecked")));
    }

    private static JDecl.Method textSubstring() {
        JExpr value = name("value", STRING);
        return method(PUBLIC_STATIC, STRING, "textSubstring",
                List.of(parameter(STRING, "value"), parameter(OBJECT, "start")),
                List.of(new JStmt.Return(call(null, JTypes.programClass(), "textSubstring",
                        STRING, value, name("start", OBJECT),
                        call(value, STRING, "length", INT)))));
    }

    private static JDecl.Method textSubstringRange() {
        JExpr value = name("value", STRING);
        JExpr from = name("from", INT);
        JExpr to = name("to", INT);
        JExpr message = new JExpr.Binary(JExpr.BinaryOp.PLUS,
                new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS,
                                new JExpr.Binary(JExpr.BinaryOp.PLUS, str("substring("),
                                        from, STRING), str(", "), STRING), to, STRING),
                str(") ends before it starts"), STRING);
        return method(PUBLIC_STATIC, STRING, "textSubstring",
                List.of(parameter(STRING, "value"), parameter(OBJECT, "start"),
                        parameter(OBJECT, "end")),
                List.of(
                        new JStmt.VarDecl(INT, "from", false, call(null,
                                JTypes.programClass(), "textIndex", INT, value,
                                name("start", OBJECT)), List.of()),
                        new JStmt.VarDecl(INT, "to", false, call(null,
                                JTypes.programClass(), "textIndex", INT, value,
                                name("end", OBJECT)), List.of()),
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.GREATER, from, to, BOOLEAN),
                                new JStmt.Throw(call(null, JTypes.programClass(), "fail",
                                        JTypes.RUNTIME_EXCEPTION, str("SOL-R009"), message)),
                                null),
                        new JStmt.Return(call(value, STRING, "substring", STRING, from, to))));
    }

    private static JDecl.Method textIndex() {
        JExpr value = name("value", STRING);
        JExpr index = name("index", BIG_INTEGER);
        JExpr invalid = new JExpr.Binary(JExpr.BinaryOp.OR,
                new JExpr.Binary(JExpr.BinaryOp.LESS,
                        call(index, BIG_INTEGER, "signum", INT), intLiteral(0), BOOLEAN),
                new JExpr.Binary(JExpr.BinaryOp.GREATER,
                        call(index, BIG_INTEGER, "compareTo", INT,
                                call(null, BIG_INTEGER, "valueOf", BIG_INTEGER,
                                        call(value, STRING, "length", INT))),
                        intLiteral(0), BOOLEAN), BOOLEAN);
        JExpr message = new JExpr.Binary(JExpr.BinaryOp.PLUS,
                new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS, str("text index "), index, STRING),
                        str(" is outside 0.."), STRING),
                call(value, STRING, "length", INT), STRING);
        return method(PRIVATE_STATIC, INT, "textIndex",
                List.of(parameter(STRING, "value"), parameter(OBJECT, "position")),
                List.of(
                        new JStmt.VarDecl(BIG_INTEGER, "index", false, call(null,
                                JTypes.programClass(), "toBigInteger", BIG_INTEGER,
                                name("position", OBJECT)), List.of()),
                        ifStatement(invalid, new JStmt.Throw(call(null,
                                JTypes.programClass(), "fail", JTypes.RUNTIME_EXCEPTION,
                                str("SOL-R009"), message)), null),
                        new JStmt.Return(call(index, BIG_INTEGER, "intValue", INT))));
    }

    private static JDecl.Method textSplit() {
        JExpr value = name("value", STRING);
        JExpr separator = name("separator", STRING);
        JExpr split = call(value, STRING, "split", new JType.Array(STRING),
                call(null, JTypes.PATTERN, "quote", STRING, separator), intLiteral(-1));
        return method(PUBLIC_STATIC, JTypes.list(STRING), "textSplit",
                List.of(parameter(STRING, "value"), parameter(STRING, "separator")),
                List.of(
                        ifStatement(call(separator, STRING, "isEmpty", BOOLEAN),
                                new JStmt.Throw(call(null, JTypes.programClass(), "fail",
                                        JTypes.RUNTIME_EXCEPTION, str("SOL-R010"),
                                        str("split needs a non-empty separator"))), null),
                        new JStmt.Return(call(null, JTypes.ARRAYS, "asList",
                                JTypes.list(STRING), split))));
    }

    private static JDecl.Method toByte() {
        return conversionMethod("toByte", JTypes.BYTE, "byteValueExact");
    }

    private static JDecl.Method toShort() {
        return conversionMethod("toShort", JTypes.SHORT, "shortValueExact");
    }

    private static JDecl.Method toLong() {
        return conversionMethod("toLong", JTypes.LONG, "longValueExact");
    }

    private static JDecl.Method conversionMethod(String name, JType.Declared target,
            String exactMethod) {
        JExpr value = name("value", OBJECT);
        return method(PUBLIC_STATIC, target, name, List.of(parameter(OBJECT, "value")),
                List.of(new JStmt.Try(new JStmt.Block(List.of(new JStmt.Return(
                        call(call(null, JTypes.programClass(), "toBigInteger", BIG_INTEGER, value),
                                BIG_INTEGER, exactMethod, primitiveOf(target))))),
                        List.of(new JStmt.Catch(ARITHMETIC_EXCEPTION, "invalid",
                                new JStmt.Block(List.of(new JStmt.Throw(conversionCall(value,
                                        targetName(target))))))))));
    }

    private static JDecl.Method toInteger() {
        JExpr value = name("value", OBJECT);
        JExpr converted = call(call(null, JTypes.programClass(), "toBigInteger", BIG_INTEGER,
                value), BIG_INTEGER, "intValueExact", INT);
        return method(PUBLIC_STATIC, JTypes.INTEGER, "toInteger",
                List.of(parameter(OBJECT, "value")),
                List.of(
                        new JStmt.ExpressionStatement(call(null, JTypes.programClass(),
                                "required", JTypes.VOID, value)),
                        new JStmt.Try(new JStmt.Block(List.of(new JStmt.Return(converted))),
                                List.of(
                                        new JStmt.Catch(ARITHMETIC_EXCEPTION, "invalid",
                                                new JStmt.Block(List.of(new JStmt.Throw(
                                                        conversionCall(value, "Integer"))))),
                                        new JStmt.Catch(ERROR, "invalid",
                                                new JStmt.Block(List.of(new JStmt.Throw(
                                                        conversionCall(value, "Integer")))))))));
    }

    private static JDecl.Method toBigInteger() {
        JExpr value = name("value", OBJECT);
        JExpr text = name("text", STRING);
        List<JStmt> body = new ArrayList<>();
        body.add(new JStmt.ExpressionStatement(call(null, JTypes.programClass(), "required",
                JTypes.VOID, value)));
        body.add(ifStatement(new JExpr.InstanceOf(value, BIG_INTEGER, "integer"),
                new JStmt.Return(name("integer", BIG_INTEGER)), null));
        body.add(ifStatement(integralCarrier(value),
                new JStmt.Return(call(null, BIG_INTEGER, "valueOf", BIG_INTEGER,
                        call(cast(JTypes.NUMBER, value), JTypes.NUMBER, "longValue", LONG))),
                null));
        List<JStmt> tryBody = new ArrayList<>();
        tryBody.add(ifStatement(new JExpr.InstanceOf(value, STRING, "text"),
                new JStmt.Block(List.of(
                        ifStatement(new JExpr.Unary(JExpr.UnaryOp.NOT,
                                call(null, JTypes.programClass(), "isIntegerText", BOOLEAN, text),
                                BOOLEAN),
                                new JStmt.Throw(conversionCall(value, "BigInteger")), null),
                        new JStmt.Return(new JExpr.New(BIG_INTEGER, List.of(text),
                                BIG_INTEGER)))), null));
        tryBody.add(new JStmt.Return(call(
                call(null, JTypes.programClass(), "toBigDecimal", BIG_DECIMAL, value),
                BIG_DECIMAL, "toBigIntegerExact", BIG_INTEGER)));
        body.add(new JStmt.Try(new JStmt.Block(tryBody),
                List.of(new JStmt.Catch(ARITHMETIC_EXCEPTION, "invalid",
                        new JStmt.Block(List.of(new JStmt.Throw(
                                conversionCall(value, "BigInteger"))))))));
        return method(PUBLIC_STATIC, BIG_INTEGER, "toBigInteger",
                List.of(parameter(OBJECT, "value")), body);
    }

    private static JDecl.Method toBigDecimal() {
        JExpr value = name("value", OBJECT);
        JExpr text = name("text", STRING);
        List<JStmt> body = new ArrayList<>();
        body.add(new JStmt.ExpressionStatement(call(null, JTypes.programClass(), "required",
                JTypes.VOID, value)));
        body.add(ifStatement(new JExpr.InstanceOf(value, BIG_DECIMAL, "decimal"),
                new JStmt.Return(name("decimal", BIG_DECIMAL)), null));
        body.add(ifStatement(new JExpr.InstanceOf(value, BIG_INTEGER, "integer"),
                new JStmt.Return(new JExpr.New(BIG_DECIMAL,
                        List.of(name("integer", BIG_INTEGER)), BIG_DECIMAL)), null));
        body.add(ifStatement(integralCarrier(value),
                new JStmt.Return(call(null, BIG_DECIMAL, "valueOf", BIG_DECIMAL,
                        call(cast(JTypes.NUMBER, value), JTypes.NUMBER, "longValue", LONG))),
                null));
        List<JStmt> tryBody = new ArrayList<>();
        tryBody.add(ifStatement(floatingCarrier(value),
                new JStmt.Return(new JExpr.New(BIG_DECIMAL,
                        List.of(call(value, OBJECT, "toString", STRING)), BIG_DECIMAL)), null));
        tryBody.add(ifStatement(new JExpr.Binary(JExpr.BinaryOp.AND,
                        new JExpr.InstanceOf(value, STRING, "text"),
                        call(null, JTypes.programClass(), "isDecimalText", BOOLEAN, text),
                        BOOLEAN),
                new JStmt.Return(new JExpr.New(BIG_DECIMAL, List.of(text), BIG_DECIMAL)), null));
        body.add(new JStmt.Try(new JStmt.Block(tryBody),
                List.of(new JStmt.Catch(NUMBER_FORMAT_EXCEPTION, "invalid",
                        new JStmt.Block(List.of(new JStmt.Throw(
                                conversionCall(value, "BigDecimal"))))))));
        body.add(new JStmt.Throw(conversionCall(value, "BigDecimal")));
        return method(PUBLIC_STATIC, BIG_DECIMAL, "toBigDecimal",
                List.of(parameter(OBJECT, "value")), body);
    }

    private static JDecl.Method toFloat() {
        return floatingConversion("toFloat", JTypes.FLOAT, "floatValue", FLOAT);
    }

    private static JDecl.Method toDouble() {
        return floatingConversion("toDouble", JTypes.DOUBLE, "doubleValue", DOUBLE);
    }

    private static JDecl.Method floatingConversion(String name, JType.Declared target,
            String javaConversion, JType primitive) {
        JExpr value = name("value", OBJECT);
        JExpr text = name("text", STRING);
        return method(PUBLIC_STATIC, target, name, List.of(parameter(OBJECT, "value")),
                List.of(
                        new JStmt.ExpressionStatement(call(null, JTypes.programClass(),
                                "required", JTypes.VOID, value)),
                        ifStatement(new JExpr.InstanceOf(value, JTypes.NUMBER, "number"),
                                new JStmt.Return(call(name("number", JTypes.NUMBER),
                                        JTypes.NUMBER, javaConversion, primitive)), null),
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.AND,
                                        new JExpr.InstanceOf(value, STRING, "text"),
                                        call(null, JTypes.programClass(), "isFloatingText", BOOLEAN,
                                                text), BOOLEAN),
                                new JStmt.Return(call(null, target, "valueOf", target, text)),
                                null),
                        new JStmt.Throw(conversionCall(value, targetName(target)))));
    }

    private static JDecl.Method toBoolean() {
        JExpr value = name("value", OBJECT);
        return method(PUBLIC_STATIC, JTypes.BOOLEAN, "toBoolean",
                List.of(parameter(OBJECT, "value")),
                List.of(
                        new JStmt.ExpressionStatement(call(null, JTypes.programClass(),
                                "required", JTypes.VOID, value)),
                        ifStatement(new JExpr.InstanceOf(value, JTypes.BOOLEAN, "flag"),
                                new JStmt.Return(name("flag", JTypes.BOOLEAN)), null),
                        ifStatement(call(str("true"), STRING, "equals", BOOLEAN, value),
                                new JStmt.Return(new JExpr.BooleanLiteral(true)), null),
                        ifStatement(call(str("false"), STRING, "equals", BOOLEAN, value),
                                new JStmt.Return(new JExpr.BooleanLiteral(false)), null),
                        new JStmt.Throw(conversionCall(value, "Boolean"))));
    }

    /**
     * Returns a value that the declaration promises is non-null, aborting with {@code SOL-R008}
     * when the Java boundary produced {@code null} anyway ({@code LANGUAGE.md} §11.1).
     */
    private static JDecl.Method checked() {
        JExpr value = name("value", OBJECT);
        return method(PUBLIC_STATIC, OBJECT, "checked", List.of(parameter(OBJECT, "value")),
                List.of(ifStatement(isNull(value), new JStmt.Throw(call(null,
                        JTypes.programClass(), "fail", JTypes.RUNTIME_EXCEPTION,
                        str("SOL-R008"),
                        str("a null value was used where a value is required"))), null),
                        new JStmt.Return(value)));
    }

    /**
     * The type-preserving form of {@link #checked()}, for a result that carries type arguments.
     *
     * <p>{@code checked} returns {@code Object}, so recovering a parameterized Solvik type from it
     * would take an unchecked cast — a warning the generated Java cannot carry. This member returns
     * its own argument type instead, so the same {@code SOL-R008} guard applies to a generic native
     * result without a cast. Only a native result with type arguments reaches it; every other
     * carrier keeps the {@code checked} call site byte for byte.
     */
    private static JDecl.Method checkedResult() {
        JType variable = new JType.TypeVariable("T");
        JExpr value = name("value", variable);
        return new JDecl.Method(PUBLIC_STATIC, List.of("T"), variable, "checkedResult",
                List.of(parameter(variable, "value")),
                List.of(ifStatement(isNull(value), new JStmt.Throw(call(null,
                        JTypes.programClass(), "fail", JTypes.RUNTIME_EXCEPTION,
                        str("SOL-R008"),
                        str("a null value was used where a value is required"))), null),
                        new JStmt.Return(value)),
                List.of());
    }

    private static JDecl.Method required() {
        JExpr value = name("value", OBJECT);
        return method(PRIVATE_STATIC, JTypes.VOID, "required",
                List.of(parameter(OBJECT, "value")),
                List.of(ifStatement(isNull(value), new JStmt.Throw(call(null,
                        JTypes.programClass(), "fail", JTypes.RUNTIME_EXCEPTION,
                        str("SOL-R008"),
                        str("a null value was used where a value is required"))), null)));
    }

    private static JDecl.Method conversion() {
        JExpr value = name("value", OBJECT);
        JExpr target = name("target", STRING);
        JExpr message = new JExpr.Binary(JExpr.BinaryOp.PLUS,
                new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS, str("cannot convert '"),
                                call(null, JTypes.programClass(), "render", STRING, value),
                                STRING),
                        str("' to "), STRING), target, STRING);
        return method(PRIVATE_STATIC, JTypes.RUNTIME_EXCEPTION, "conversion",
                List.of(parameter(OBJECT, "value"), parameter(STRING, "target")),
                List.of(new JStmt.Return(call(null, JTypes.programClass(), "fail",
                        JTypes.RUNTIME_EXCEPTION, str("SOL-R006"), message))));
    }

    private static List<JDecl.Member> patternTextMembers(String methodName, String fieldName,
            String pattern) {
        JDecl.Field field = JDecl.Field.of(Set.of(JDecl.Modifier.PRIVATE, JDecl.Modifier.STATIC,
                JDecl.Modifier.FINAL), JTypes.PATTERN, fieldName, call(null, JTypes.PATTERN,
                        "compile", JTypes.PATTERN, str(pattern)));
        return List.of(field, patternText(methodName, fieldName));
    }

    private static JDecl.Method floatingText() {
        JExpr text = name("text", STRING);
        JExpr test = call(null, JTypes.programClass(), "isDecimalText", BOOLEAN, text);
        for (String value : List.of("NaN", "Infinity", "+Infinity", "-Infinity")) {
            test = new JExpr.Binary(JExpr.BinaryOp.OR, test,
                    call(str(value), STRING, "equals", BOOLEAN, text), BOOLEAN);
        }
        return method(PRIVATE_STATIC, BOOLEAN, "isFloatingText", List.of(parameter(STRING, "text")),
                List.of(new JStmt.Return(test)));
    }

    private static JDecl.Method patternText(String methodName, String fieldName) {
        JExpr text = name("text", STRING);
        return method(PRIVATE_STATIC, BOOLEAN, methodName, List.of(parameter(STRING, "text")),
                List.of(new JStmt.Return(call(
                        call(name(fieldName, JTypes.PATTERN), JTypes.PATTERN, "matcher", MATCHER,
                                text),
                        MATCHER, "matches", BOOLEAN))));
    }

    private static JDecl.Method divideInt() {
        return arithmeticInt("divide", "SOL-R001", "division by zero", JExpr.BinaryOp.DIVIDE);
    }

    private static JDecl.Method divideLong() {
        return arithmeticLong("divide", "SOL-R001", "division by zero", JExpr.BinaryOp.DIVIDE);
    }

    private static JDecl.Method remainderInt() {
        return arithmeticInt("remainder", "SOL-R002", "remainder by zero",
                JExpr.BinaryOp.REMAINDER);
    }

    private static JDecl.Method remainderLong() {
        return arithmeticLong("remainder", "SOL-R002", "remainder by zero",
                JExpr.BinaryOp.REMAINDER);
    }

    private static JDecl.Method arithmeticInt(String name, String code, String message,
            JExpr.BinaryOp operator) {
        JExpr a = name("a", INT);
        JExpr b = name("b", INT);
        return method(PUBLIC_STATIC, INT, name, List.of(parameter(INT, "a"), parameter(INT, "b")),
                List.of(
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.EQUAL, b, intLiteral(0),
                                BOOLEAN), new JStmt.Throw(call(null, JTypes.programClass(),
                                "fail", JTypes.RUNTIME_EXCEPTION, str(code), str(message))), null),
                        new JStmt.Return(new JExpr.Binary(operator, a, b, INT))));
    }

    private static JDecl.Method arithmeticLong(String name, String code, String message,
            JExpr.BinaryOp operator) {
        JExpr a = name("a", LONG);
        JExpr b = name("b", LONG);
        return method(PUBLIC_STATIC, LONG, name,
                List.of(parameter(LONG, "a"), parameter(LONG, "b")),
                List.of(
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.EQUAL, b, longLiteral(0),
                                BOOLEAN), new JStmt.Throw(call(null, JTypes.programClass(),
                                "fail", JTypes.RUNTIME_EXCEPTION, str(code), str(message))), null),
                        new JStmt.Return(new JExpr.Binary(operator, a, b, LONG))));
    }

    private static JDecl.Method divideBigInteger() {
        return integerDivide("divide", "SOL-R001", "division by zero", "divide");
    }

    private static JDecl.Method remainderBigInteger() {
        return integerDivide("remainder", "SOL-R002", "remainder by zero", "remainder");
    }

    private static JDecl.Method integerDivide(String name, String code, String message,
            String javaMethod) {
        JExpr a = name("a", BIG_INTEGER);
        JExpr b = name("b", BIG_INTEGER);
        return method(PUBLIC_STATIC, BIG_INTEGER, name,
                List.of(parameter(BIG_INTEGER, "a"), parameter(BIG_INTEGER, "b")),
                List.of(
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.EQUAL,
                                call(b, BIG_INTEGER, "signum", INT), intLiteral(0), BOOLEAN),
                                new JStmt.Throw(call(null, JTypes.programClass(), "fail",
                                        JTypes.RUNTIME_EXCEPTION,
                                        str(code), str(message))), null),
                        new JStmt.Return(call(a, BIG_INTEGER, javaMethod, BIG_INTEGER, b))));
    }

    private static JDecl.Method divideBigDecimal() {
        JExpr a = name("a", BIG_DECIMAL);
        JExpr b = name("b", BIG_DECIMAL);
        JExpr message = new JExpr.Binary(JExpr.BinaryOp.PLUS,
                new JExpr.Binary(JExpr.BinaryOp.PLUS,
                        new JExpr.Binary(JExpr.BinaryOp.PLUS,
                                str("BigDecimal division has no terminating result: "),
                                a, STRING), str(" / "), STRING), b, STRING);
        return method(PUBLIC_STATIC, BIG_DECIMAL, "divide",
                List.of(parameter(BIG_DECIMAL, "a"), parameter(BIG_DECIMAL, "b")),
                List.of(
                        ifStatement(new JExpr.Binary(JExpr.BinaryOp.EQUAL,
                                call(b, BIG_DECIMAL, "signum", INT), intLiteral(0), BOOLEAN),
                                new JStmt.Throw(call(null, JTypes.programClass(), "fail",
                                        JTypes.RUNTIME_EXCEPTION,
                                        str("SOL-R001"), str("division by zero"))), null),
                        new JStmt.Try(new JStmt.Block(List.of(new JStmt.Return(
                                call(a, BIG_DECIMAL, "divide", BIG_DECIMAL, b)))),
                                List.of(new JStmt.Catch(ARITHMETIC_EXCEPTION, "invalid",
                                        new JStmt.Block(List.of(new JStmt.Throw(call(null,
                                                JTypes.programClass(), "fail",
                                                JTypes.RUNTIME_EXCEPTION, str("SOL-R003"),
                                                message)))))))));
    }

    private static JDecl.Method type(Map<String, Integer> userTypes, boolean usesException) {
        JExpr value = name("value", OBJECT);
        List<JStmt> body = new ArrayList<>();
        body.add(ifStatement(isNull(value), new JStmt.Return(str("Null")), null));
        body.add(ifStatement(new JExpr.InstanceOf(value, JTypes.listOfAny(), null),
                new JStmt.Return(str("Array")), null));
        if (usesException) {
            // Named here so a raised value reports the Solvik type, not its generated class name.
            body.add(ifStatement(new JExpr.InstanceOf(value, EXCEPTION, null),
                    new JStmt.Return(str("Exception")), null));
        }
        for (Map.Entry<String, Integer> declared : userTypes.entrySet()) {
            List<JType> arguments = new ArrayList<>();
            for (int i = 0; i < declared.getValue(); i++) {
                arguments.add(JType.Wildcard.any());
            }
            JType.Declared tested = JTypes.generated(declared.getKey())
                    .withArguments(arguments);
            body.add(ifStatement(new JExpr.InstanceOf(value, tested, "userType"),
                    new JStmt.Return(call(name("userType", tested), tested, "solvikTypeName",
                            STRING)), null));
        }
        body.add(new JStmt.Return(call(call(value, OBJECT, "getClass",
                JType.Declared.of("java.lang", "Class").withArguments(
                        List.of(JType.Wildcard.any()))),
                JType.Declared.of("java.lang", "Class").withArguments(
                        List.of(JType.Wildcard.any())), "getSimpleName", STRING)));
        return method(PUBLIC_STATIC, STRING, "type", List.of(parameter(OBJECT, "value")), body);
    }

    private static JDecl.Method plainText() {
        JExpr value = name("value", OBJECT);
        return method(PUBLIC_STATIC, STRING, "plainText", List.of(parameter(OBJECT, "value")),
                List.of(
                        ifStatement(isNull(value), new JStmt.Return(str("null")), null),
                        new JStmt.Return(call(cast(BIG_DECIMAL, value), BIG_DECIMAL,
                                "toPlainString", STRING))));
    }

    // ----------------------------------------------------------------- dependencies

    /** Collects the support members a group's own IR reaches. */
    private static void collectDependencies(List<JDecl.Member> members, Set<String> into) {
        for (JDecl.Member member : members) {
            collectMember(member, into);
        }
    }

    private static void collectMember(JDecl.Member member, Set<String> into) {
        switch (member) {
            case JDecl.Field field -> collectExpr(field.initializer(), into);
            case JDecl.Constructor constructor -> collectStatements(constructor.body(), into);
            case JDecl.Method method -> collectStatements(method.body(), into);
            case JDecl.StaticInitializer initializer ->
                    collectStatements(initializer.body(), into);
            case JDecl.ClassDecl nested -> {
                for (JDecl.Member child : nested.members()) {
                    collectMember(child, into);
                }
            }
        }
    }

    private static void collectStatements(List<JStmt> statements, Set<String> into) {
        if (statements == null) {
            return;
        }
        for (JStmt statement : statements) {
            collectStatement(statement, into);
        }
    }

    private static void collectStatement(JStmt statement, Set<String> into) {
        switch (statement) {
            case JStmt.Block block -> collectStatements(block.statements(), into);
            case JStmt.VarDecl declaration -> collectExpr(declaration.initializer(), into);
            case JStmt.ExpressionStatement expression ->
                    collectExpr(expression.expression(), into);
            case JStmt.If branch -> {
                collectExpr(branch.condition(), into);
                collectStatement(branch.thenBranch(), into);
                if (branch.elseBranch() != null) {
                    collectStatement(branch.elseBranch(), into);
                }
            }
            case JStmt.While loop -> {
                collectExpr(loop.condition(), into);
                collectStatement(loop.body(), into);
            }
            case JStmt.ForEach loop -> {
                collectExpr(loop.iterable(), into);
                collectStatement(loop.body(), into);
            }
            case JStmt.For loop -> {
                collectStatements(loop.init(), into);
                collectExpr(loop.condition(), into);
                collectStatements(loop.update(), into);
                collectStatement(loop.body(), into);
            }
            case JStmt.Switch selection -> {
                collectExpr(selection.selector(), into);
                for (JStmt.SwitchArm arm : selection.arms()) {
                    collectExpr(arm.label(), into);
                    collectStatements(arm.body(), into);
                }
                if (selection.defaultBody() != null) {
                    collectStatement(selection.defaultBody(), into);
                }
            }
            case JStmt.Return returned -> collectExpr(returned.value(), into);
            case JStmt.Throw thrown -> collectExpr(thrown.exception(), into);
            case JStmt.Try attempt -> {
                collectStatement(attempt.body(), into);
                for (JStmt.Catch catcher : attempt.catches()) {
                    collectType(catcher.type(), into);
                    collectStatement(catcher.body(), into);
                }
            }
            case JStmt.Synchronized guarded -> {
                collectExpr(guarded.monitor(), into);
                collectStatement(guarded.body(), into);
            }
            case JStmt.SuperCall call -> call.arguments().forEach(a -> collectExpr(a, into));
            case JStmt.Break ignored -> { }
            case JStmt.Continue ignored -> { }
        }
    }

    private static void collectExpr(JExpr expression, Set<String> into) {
        switch (expression) {
            case null -> { }
            case JExpr.IntLiteral ignored -> { }
            case JExpr.FloatingLiteral ignored -> { }
            case JExpr.StringLiteral ignored -> { }
            case JExpr.BooleanLiteral ignored -> { }
            case JExpr.NullLiteral ignored -> { }
            case JExpr.Name name -> collectType(name.type(), into);
            case JExpr.FieldAccess access -> {
                collectExpr(access.target(), into);
                collectType(access.type(), into);
            }
            case JExpr.StaticField field -> {
                collectType(field.owner(), into);
                collectType(field.type(), into);
            }
            case JExpr.Call call -> {
                collectType(call.owner(), into);
                if (call.receiver() == null
                        && call.owner().hasBinaryName(GeneratedNames.PROGRAM_BINARY_NAME)) {
                    into.add(call.name());
                }
                collectExpr(call.receiver(), into);
                call.typeArguments().forEach(t -> collectType(t, into));
                call.arguments().forEach(a -> collectExpr(a, into));
                collectType(call.type(), into);
            }
            case JExpr.New created -> {
                collectType(created.created(), into);
                created.arguments().forEach(a -> collectExpr(a, into));
            }
            case JExpr.NewArray array -> {
                collectType(array.elementType(), into);
                array.elements().forEach(e -> collectExpr(e, into));
            }
            case JExpr.ArrayAccess access -> {
                collectExpr(access.array(), into);
                collectExpr(access.index(), into);
            }
            case JExpr.Binary binary -> {
                collectExpr(binary.left(), into);
                collectExpr(binary.right(), into);
            }
            case JExpr.Unary unary -> collectExpr(unary.operand(), into);
            case JExpr.Cast cast -> {
                collectType(cast.target(), into);
                collectExpr(cast.operand(), into);
            }
            case JExpr.Conditional conditional -> {
                collectExpr(conditional.condition(), into);
                collectExpr(conditional.whenTrue(), into);
                collectExpr(conditional.whenFalse(), into);
            }
            case JExpr.Assignment assignment -> {
                collectExpr(assignment.target(), into);
                collectExpr(assignment.value(), into);
            }
            case JExpr.InstanceOf test -> collectExpr(test.operand(), into);
        }
    }

    private static void collectType(JType type, Set<String> into) {
        switch (type) {
            case null -> { }
            case JType.Declared declared -> {
                if (declared.packageName().equals(GeneratedNames.GLOBAL_PACKAGE)
                        && declared.enclosing().contains(GeneratedNames.PROGRAM_CLASS)) {
                    into.add(declared.name());
                }
                declared.arguments().forEach(argument -> collectType(argument, into));
            }
            case JType.Array array -> collectType(array.element(), into);
            case JType.Wildcard wildcard -> collectType(wildcard.bound(), into);
            default -> { }
        }
    }

    // ---------------------------------------------------------------------- helpers

    private static JDecl.Method method(Set<JDecl.Modifier> modifiers, JType returnType,
            String name, List<JDecl.Parameter> parameters, List<JStmt> body) {
        return new JDecl.Method(modifiers, List.of(), returnType, name, parameters, body,
                List.of());
    }

    private static JDecl.Parameter parameter(JType type, String name) {
        return new JDecl.Parameter(type, name);
    }

    private static JDecl.Annotation suppression(String value) {
        return new JDecl.Annotation(JType.Declared.of("java.lang", "SuppressWarnings"),
                List.of(str(value)));
    }

    private static JExpr name(String name, JType type) {
        return new JExpr.Name(name, type);
    }

    private static JExpr field(String target, String name, JType type) {
        return new JExpr.FieldAccess(name(target, type), name, type);
    }

    private static JExpr systemField(String name) {
        return new JExpr.StaticField(JTypes.SYSTEM, name, JTypes.PRINT_STREAM);
    }

    private static JExpr str(String value) {
        return new JExpr.StringLiteral(value);
    }

    private static JExpr intLiteral(int value) {
        return new JExpr.IntLiteral(Integer.toString(value), INT);
    }

    private static JExpr longLiteral(long value) {
        return new JExpr.IntLiteral(Long.toString(value), LONG);
    }

    private static JExpr cast(JType target, JExpr operand) {
        return new JExpr.Cast(target, operand);
    }

    private static JExpr isNull(JExpr expression) {
        return new JExpr.Binary(JExpr.BinaryOp.EQUAL, expression, new JExpr.NullLiteral(),
                BOOLEAN);
    }

    private static JExpr reference(JExpr left, JExpr right) {
        return new JExpr.Binary(JExpr.BinaryOp.EQUAL, left, right, BOOLEAN);
    }

    private static JExpr notEqual(JExpr expression, JExpr value) {
        return new JExpr.Binary(JExpr.BinaryOp.NOT_EQUAL, expression, value, BOOLEAN);
    }

    private static JExpr length(JExpr array) {
        return new JExpr.FieldAccess(array, "length", INT);
    }

    private static JExpr append(JExpr builder, String value) {
        return append(builder, str(value));
    }

    private static JExpr append(JExpr builder, JExpr value) {
        return call(builder, JTypes.STRING_BUILDER, "append", JTypes.STRING_BUILDER, value);
    }

    private static JExpr conversionCall(JExpr value, String target) {
        return call(null, JTypes.programClass(), "conversion", JTypes.RUNTIME_EXCEPTION, value,
                str(target));
    }

    private static JExpr integralCarrier(JExpr value) {
        JExpr test = new JExpr.InstanceOf(value, JTypes.BYTE, null);
        for (JType carrier : List.of(JTypes.SHORT, JTypes.INTEGER, JTypes.LONG)) {
            test = new JExpr.Binary(JExpr.BinaryOp.OR, test,
                    new JExpr.InstanceOf(value, carrier, null), BOOLEAN);
        }
        return test;
    }

    private static JExpr floatingCarrier(JExpr value) {
        return new JExpr.Binary(JExpr.BinaryOp.OR,
                new JExpr.InstanceOf(value, JTypes.FLOAT, null),
                new JExpr.InstanceOf(value, JTypes.DOUBLE, null), BOOLEAN);
    }

    private static JType primitiveOf(JType.Declared type) {
        if (type.equals(JTypes.BYTE)) {
            return BYTE;
        }
        if (type.equals(JTypes.SHORT)) {
            return SHORT;
        }
        if (type.equals(JTypes.INTEGER)) {
            return INT;
        }
        if (type.equals(JTypes.LONG)) {
            return LONG;
        }
        throw new IllegalArgumentException("not an integral carrier: " + type);
    }

    private static String targetName(JType.Declared type) {
        if (type.equals(JTypes.BYTE)) {
            return "Byte";
        }
        if (type.equals(JTypes.SHORT)) {
            return "Short";
        }
        if (type.equals(JTypes.INTEGER)) {
            return "Integer";
        }
        if (type.equals(JTypes.LONG)) {
            return "Long";
        }
        if (type.equals(JTypes.FLOAT)) {
            return "Float";
        }
        if (type.equals(JTypes.DOUBLE)) {
            return "Double";
        }
        throw new IllegalArgumentException("not a numeric carrier: " + type);
    }

    private static JExpr call(JExpr receiver, JType.Declared owner, String name, JType returnType,
            JExpr... arguments) {
        return new JExpr.Call(receiver, owner, name, List.of(), List.of(arguments), returnType);
    }

    private static JExpr call(JType.Declared owner, String name, JType returnType,
            JExpr... arguments) {
        return new JExpr.Call(null, owner, name, List.of(), List.of(arguments), returnType);
    }

    private static JStmt ifStatement(JExpr condition, JStmt then, JStmt otherwise) {
        return new JStmt.If(condition, then, otherwise);
    }

    private static JExpr assign(JExpr target, JExpr value, JType type) {
        return new JExpr.Assignment(target, value, type);
    }
}
