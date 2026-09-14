package org.solvik.transpiler.backend;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.solvik.transpiler.InternalCompilerException;
import org.solvik.transpiler.SolvikIr;
import org.solvik.transpiler.SolvikProgram;
import org.solvik.transpiler.SolvikStmt;
import org.solvik.transpiler.language.Literals;
import org.solvik.transpiler.TypeModel;
import org.solvik.transpiler.TypeModel.Base;
import org.solvik.transpiler.TypeModel.Type;

import static org.solvik.transpiler.TypeModel.*;
import static org.solvik.transpiler.language.Language.*;

/**
 * Backend lowering phase: typed Solvik IR to Java expression IR.
 *
 * <p>Every Java representation decision belongs here: Solvik to Java type
 * spelling, checked arithmetic, boxing, casts, runtime-helper selection, Java
 * method/constructor calls, enum representation, and identifier mangling. The
 * result is {@link JavaIr}, which {@link JavaEmitter} only renders.</p>
 *
 * <p>The lowerer also records required {@link RuntimeFeature}s at the exact
 * decision points that introduce them; the emitter never scans rendered text to
 * rediscover them.</p>
 */
public final class JavaLowerer {
    private final JavaProgram program;
    private final JavaIrOptimizer optimizer = new JavaIrOptimizer();

    public JavaLowerer(JavaProgram program) {
        this.program = program;
    }

    /** Lowers and renders one Solvik expression to Java source text. */
    public String emit(SolvikIr expression) {
        return JavaIr.render(optimizer.optimize(lower(expression)));
    }

    /** Lowers a Solvik expression to structured Java expression IR. */
    public JavaIr lower(SolvikIr expression) {
        if (expression instanceof SolvikIr.Self s) return new JavaIr.This(s.type());
        if (expression instanceof SolvikIr.Local l) return new JavaIr.Identifier(localName(l.name()), l.type());
        if (expression instanceof SolvikIr.Field f) {
            return f.isStatic()
                    ? JavaIr.staticField(program.structName(f.owner()), fieldName(f.name()), f.type())
                    : JavaIr.fieldAccess(new JavaIr.This(f.type()), fieldName(f.name()), f.type());
        }
        if (expression instanceof SolvikIr.Literal l) return JavaIr.literal(literalJava(l.kind(), l.text(), l.type()), l.type());
        if (expression instanceof SolvikIr.ListLiteral l) {
            program.require(RuntimeFeature.LIST);
            return new JavaIr.StaticCall("RT", "<" + javaType(l.elementType(), true) + ">", "list", lowerAll(l.elements()), l.type());
        }
        if (expression instanceof SolvikIr.MapLiteral m) {
            program.require(RuntimeFeature.MAP);
            List<JavaIr> args = new ArrayList<>();
            for (java.util.Map.Entry<SolvikIr, SolvikIr> entry : m.entries()) { args.add(lower(entry.getKey())); args.add(lower(entry.getValue())); }
            return new JavaIr.StaticCall("RT", "<" + javaType(m.keyType(), true) + ", " + javaType(m.valueType(), true) + ">", "map", args, m.type());
        }
        if (expression instanceof SolvikIr.Member m) {
            JavaIr receiver = m.receiver().type() != null && m.receiver().type().nullable()
                    ? JavaIr.call("RT.require", List.of(lower(m.receiver())), m.receiver().type())
                    : lower(m.receiver());
            return JavaIr.fieldAccess(receiver, m.isField() ? fieldName(m.name()) : methodName(m.name()), m.type());
        }
        if (expression instanceof SolvikIr.StaticRef s) return staticRefIr(s.name(), s.type());
        if (expression instanceof SolvikIr.Assign a) return new JavaIr.Assignment(lower(a.target()), lower(a.value()), a.type());
        if (expression instanceof SolvikIr.DefaultValue d) return JavaIr.literal(defaultValue(d.type()), d.type());
        if (expression instanceof SolvikIr.Coerce c) return lowerCoerce(c);
        if (expression instanceof SolvikIr.SelfInit s) {
            List<JavaIr> args = lowerAll(s.values());
            SolvikProgram.Struct struct = program.structDecl(s.owner());
            String typeArguments = struct == null ? "" : typeArgumentsOf(struct.typeParameters());
            return new JavaIr.New(program.structName(s.owner()), typeArguments, args, s.type());
        }
        if (expression instanceof SolvikIr.Call c) return c.builtin() != null ? javaBuiltin(c) : javaCall(c);
        if (expression instanceof SolvikIr.Update u) return lowerUpdate(u);
        if (expression instanceof SolvikIr.Unary u) return javaUnary(u);
        if (expression instanceof SolvikIr.Binary b) return javaBinary(b);
        if (expression instanceof SolvikIr.Coalesce c) {
            JavaIr l = lower(c.left());
            JavaIr r = lower(c.right());
            Type t = c.type();
            return JavaIr.paren(JavaIr.ternary(JavaIr.infix("!=", l, JavaIr.literal("null", t), t), l, r, t), t);
        }
        if (expression instanceof SolvikIr.Range r) {
            program.require(RuntimeFeature.RANGE);
            return JavaIr.call("RT.range", List.of(lower(r.start()), lower(r.end()), JavaIr.literal(Boolean.toString(r.inclusive()), named(Base.BOOLEAN, "Boolean"))), r.type());
        }
        if (expression instanceof SolvikIr.Match m) return new JavaIr.Inline(javaMatch(m), m.type());
        throw new InternalCompilerException("unknown Solvik IR node: " + expression);
    }

    private JavaIr lowerCoerce(SolvikIr.Coerce c) {
        JavaIr value = lower(c.value());
        Type to = c.to();
        if (c.constantInteger()) return JavaIr.cast(to.base() == Base.BYTE ? "byte" : "short", value, to);
        if (to.nullable()) {
            String wrapper = switch (to.base()) {
                case LONG -> "Long"; case FLOAT -> "Float"; case DOUBLE -> "Double";
                case INTEGER -> "Integer"; case SHORT -> "Short"; case BYTE -> "Byte";
                default -> null;
            };
            if (wrapper != null) return JavaIr.call(wrapper + ".valueOf", List.of(JavaIr.cast(primitiveCast(to.base()), value, to)), to);
            return value;
        }
        return JavaIr.cast(primitiveCast(to.base()), value, to);
    }

    private JavaIr lowerUpdate(SolvikIr.Update u) {
        JavaIr target = lower(u.target());
        Type t = u.targetType();
        if (u.op() == BinaryOp.CONCAT) {
            JavaIr concat = concatenation(target, lower(u.value()), t, u.valueType(), t);
            return new JavaIr.Assignment(target, concat, t);
        }
        JavaIr operation = arithmetic(u.op(), target, lower(u.value()), u.promoted(), t);
        if (t.base() == Base.BYTE || t.base() == Base.SHORT) {
            program.require(RuntimeFeature.CONVERSIONS);
            operation = JavaIr.call(t.base() == Base.BYTE ? "RT.convertByte" : "RT.convertShort", List.of(operation), t);
        }
        return new JavaIr.Assignment(target, operation, t);
    }

    private List<JavaIr> lowerAll(List<SolvikIr> expressions) {
        if (expressions.isEmpty()) return List.of();
        List<JavaIr> result = new ArrayList<>(expressions.size());
        for (SolvikIr expression : expressions) result.add(lower(expression));
        return result;
    }

    private JavaIr javaUnary(SolvikIr.Unary u) {
        JavaIr x = lower(u.operand());
        Type t = u.operandType();
        Type result = u.type();
        if (u.op() == UnaryOp.NOT) return JavaIr.paren(JavaIr.prefix("!", x, result), result);
        if (t.base() == Base.INTEGER || t.base() == Base.BYTE || t.base() == Base.SHORT) return JavaIr.staticMethod("Math", "negateExact", List.of(x), result);
        if (t.base() == Base.LONG) return JavaIr.staticMethod("Math", "negateExact", List.of(x), result);
        if (t.base() == Base.BIG_INTEGER || t.base() == Base.BIG_DECIMAL) return JavaIr.method(JavaIr.paren(x, t), "negate", List.of(), result);
        return JavaIr.paren(JavaIr.prefix("-", x, result), result);
    }

    private JavaIr javaBinary(SolvikIr.Binary b) {
        JavaIr l = lower(b.left());
        JavaIr r = lower(b.right());
        Type result = b.type();
        Type leftType = b.leftType();
        Type rightType = b.rightType();
        return switch (b.op()) {
            case AND -> JavaIr.paren(JavaIr.infix("&&", l, r, result), result);
            case OR -> JavaIr.paren(JavaIr.infix("||", l, r, result), result);
            case CONCAT -> result.base() == Base.RANGE
                    ? rangeCall(l, r, result)
                    : concatenation(l, r, leftType, rightType, result);
            case ADD, SUB, MUL, DIV, MOD -> arithmetic(b.op(), l, r, result, leftType);
            case EQ, NE -> enumVariantEquality(b, l, r, leftType, rightType, result);
            case LT, LE, GT, GE -> comparison(b.op(), l, r, result, leftType, rightType);
        };
    }

    private JavaIr rangeCall(JavaIr l, JavaIr r, Type result) {
        program.require(RuntimeFeature.RANGE);
        return JavaIr.call("RT.range", List.of(l, r, JavaIr.literal("false", named(Base.BOOLEAN, "Boolean"))), result);
    }

    private boolean isNullIr(SolvikIr expression) { return expression instanceof SolvikIr.Literal l && l.kind() == LiteralKind.NULL; }

    private JavaIr enumVariantEquality(SolvikIr.Binary b, JavaIr l, JavaIr r, Type leftType, Type rightType, Type result) {
        int leftTag = enumVariantTag(b.left(), leftType);
        int rightTag = enumVariantTag(b.right(), rightType);
        boolean notEqual = b.op() == BinaryOp.NE;
        if (leftTag >= 0 && rightTag < 0 && isExactEnumType(rightType, leftType)) return JavaIr.literal("(" + JavaIr.render(r) + ".tag() " + (notEqual ? "!=" : "==") + " " + leftTag + ")", result);
        if (rightTag >= 0 && leftTag < 0 && isExactEnumType(leftType, rightType)) return JavaIr.literal("(" + JavaIr.render(l) + ".tag() " + (notEqual ? "!=" : "==") + " " + rightTag + ")", result);
        return equality(b.op(), isNullIr(b.left()), isNullIr(b.right()), l, r, leftType, rightType, result);
    }

    private int enumVariantTag(SolvikIr expression, Type type) {
        if (!(expression instanceof SolvikIr.StaticRef s) || type == null || type.base() != Base.ENUM) return -1;
        SolvikProgram.Enum declaration = program.enumDecl(type.name());
        if (declaration == null) return -1;
        List<SolvikProgram.Variant> variants = declaration.variants();
        for (int i = 0; i < variants.size(); i++) if (variants.get(i).name().equals(s.name())) return variants.get(i).payload() == null ? i : -1;
        return -1;
    }

    private boolean isExactEnumType(Type candidate, Type variantType) {
        return candidate != null && !candidate.nullable() && candidate.base() == Base.ENUM && variantType != null && candidate.name().equals(variantType.name());
    }

    private JavaIr arithmetic(BinaryOp op, JavaIr l, JavaIr r, Type result, Type left) {
        if ((op == BinaryOp.ADD || op == BinaryOp.SUB || op == BinaryOp.MUL) && result.base() == Base.INTEGER)
            return JavaIr.staticMethod("Math", exactMethod(op), List.of(l, r), result);
        if ((op == BinaryOp.ADD || op == BinaryOp.SUB || op == BinaryOp.MUL) && result.base() == Base.LONG)
            return JavaIr.staticMethod("Math", exactMethod(op), List.of(l, r), result);
        String suffix = switch (op) { case ADD -> "add"; case SUB -> "sub"; case MUL -> "mul"; case DIV -> "div"; default -> "rem"; };
        if (result.base() == Base.INTEGER) { program.require(RuntimeFeature.ARITHMETIC); return JavaIr.call("RT." + suffix + "Int", List.of(l, r), result); }
        if (result.base() == Base.LONG) { program.require(RuntimeFeature.ARITHMETIC); return JavaIr.call("RT." + suffix + "Long", List.of(l, r), result); }
        if (result.base() == Base.BIG_INTEGER) return JavaIr.method(JavaIr.paren(l, left), bigMethod(op), List.of(r), result);
        if (result.base() == Base.BIG_DECIMAL) return op == BinaryOp.DIV
                ? JavaIr.method(JavaIr.paren(l, left), "divide", List.of(r, JavaIr.literal("MathContext.DECIMAL128", result)), result)
                : JavaIr.method(JavaIr.paren(l, left), bigMethod(op), List.of(r), result);
        return JavaIr.paren(JavaIr.infix(switch (op) { case ADD -> "+"; case SUB -> "-"; case MUL -> "*"; case DIV -> "/"; default -> "%"; }, l, r, result), result);
    }

    private String exactMethod(BinaryOp op) { return switch (op) { case ADD -> "addExact"; case SUB -> "subtractExact"; default -> "multiplyExact"; }; }
    private String bigMethod(BinaryOp op) { return switch (op) { case ADD -> "add"; case SUB -> "subtract"; case MUL -> "multiply"; case DIV -> "divide"; default -> "remainder"; }; }

    private JavaIr comparison(BinaryOp op, JavaIr l, JavaIr r, Type result, Type left, Type right) {
        String operator = switch (op) { case LT -> "<"; case LE -> "<="; case GT -> ">"; default -> ">="; };
        if (result.base() == Base.BOOLEAN && !left.nullable() && !right.nullable() && left.base() == Base.STRING && right.base() == Base.STRING)
            return JavaIr.paren(JavaIr.infix(operator, JavaIr.method(l, "compareTo", List.of(r), result), JavaIr.literal("0", result), result), result);
        if (result.base() == Base.BOOLEAN && !left.nullable() && !right.nullable() && (left.base() == Base.BIG_INTEGER || left.base() == Base.BIG_DECIMAL) && left.base() == right.base())
            return JavaIr.paren(JavaIr.infix(operator, JavaIr.method(l, "compareTo", List.of(r), result), JavaIr.literal("0", result), result), result);
        if (result.base() == Base.BOOLEAN && (left.base() == Base.STRING || left.base() == Base.BIG_INTEGER || left.base() == Base.BIG_DECIMAL))
            return JavaIr.paren(JavaIr.infix(operator, JavaIr.call("RT.compare", List.of(l, r), result), JavaIr.literal("0", result), result), result);
        if (result.base() == Base.BOOLEAN) return JavaIr.paren(JavaIr.infix(operator, l, r, result), result);
        return JavaIr.literal("false", result);
    }

    private JavaIr equality(BinaryOp op, boolean leftNull, boolean rightNull, JavaIr l, JavaIr r, Type left, Type right, Type result) {
        boolean notEqual = op == BinaryOp.NE;
        JavaIr expression;
        if ((leftNull && javaReference(right)) || (rightNull && javaReference(left))) {
            return JavaIr.paren(JavaIr.infix(notEqual ? "!=" : "==", l, r, result), result);
        } else if (!left.nullable() && !right.nullable() && left.base() == Base.BOOLEAN && right.base() == Base.BOOLEAN) {
            expression = JavaIr.paren(JavaIr.infix("==", l, r, result), result);
        } else if (!left.nullable() && !right.nullable() && left.base() == Base.STRING && right.base() == Base.STRING) {
            expression = JavaIr.method(l, "equals", List.of(r), result);
        } else if (!left.nullable() && !right.nullable() && left.base() == Base.CHAR && right.base() == Base.CHAR) {
            expression = JavaIr.method(l, "equals", List.of(r), result);
        } else if ((left.base() == Base.STRING && right.base() == Base.STRING) || (left.base() == Base.CHAR && right.base() == Base.CHAR)) {
            expression = JavaIr.call("Objects.equals", List.of(l, r), result);
        } else {
            Type promoted = left.numeric() && right.numeric() ? binaryPromotion(left, right) : null;
            if (!left.nullable() && !right.nullable() && promoted != null && promoted.primitive() && promoted.base() != Base.CHAR) {
                expression = JavaIr.paren(JavaIr.infix("==", l, r, result), result);
            } else {
                expression = JavaIr.call("RT.eq", List.of(box(l, left), box(r, right)), result);
            }
        }
        return notEqual ? JavaIr.paren(JavaIr.prefix("!", expression, result), result) : expression;
    }

    private JavaIr concatenation(JavaIr l, JavaIr r, Type left, Type right, Type result) {
        if (!left.nullable() && !right.nullable() && (left.base() == Base.STRING || left.base() == Base.CHAR) && (right.base() == Base.STRING || right.base() == Base.CHAR))
            return JavaIr.paren(JavaIr.infix("+", l, r, result), result);
        if (javaStringOperand(left) && formatCompatible(right) || javaStringOperand(right) && formatCompatible(left))
            return JavaIr.paren(JavaIr.infix("+", l, r, result), result);
        return JavaIr.call("RT.cat", List.of(l, r), result);
    }

    private boolean javaStringOperand(Type type) { return type.base() == Base.STRING || type.base() == Base.CHAR; }

    private boolean formatCompatible(Type type) {
        return switch (type.base()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, LONG, BIG_INTEGER, BIG_DECIMAL, STRING, CHAR, STRUCT, TRAIT, ENUM -> true;
            default -> false;
        };
    }

    private JavaIr box(JavaIr x, Type t) {
        return t.primitive() && !t.nullable() ? switch (t.base()) {
            case BYTE -> JavaIr.call("Byte.valueOf", List.of(x), t);
            case SHORT -> JavaIr.call("Short.valueOf", List.of(x), t);
            case INTEGER -> JavaIr.call("Integer.valueOf", List.of(x), t);
            case LONG -> JavaIr.call("Long.valueOf", List.of(x), t);
            case FLOAT -> JavaIr.call("Float.valueOf", List.of(x), t);
            case DOUBLE -> JavaIr.call("Double.valueOf", List.of(x), t);
            case BOOLEAN -> JavaIr.call("Boolean.valueOf", List.of(x), t);
            default -> x;
        } : x;
    }

    private boolean javaReference(Type t) { return t.nullable() || t.base() == Base.CHAR || !t.primitive(); }

    private JavaIr checkedReceiver(SolvikIr receiver) {
        JavaIr value = lower(receiver);
        Type type = receiver.type();
        return type != null && type.nullable() ? JavaIr.call("RT.require", List.of(value), type) : value;
    }

    private String defaultValue(Type t) {
        if (t.primitive() && !t.nullable()) return switch (t.base()) {
            case BOOLEAN -> "false"; case BYTE, SHORT, INTEGER -> "0"; case LONG -> "0L";
            case FLOAT -> "0.0f"; case DOUBLE -> "0.0d"; default -> "\"\"";
        };
        return "null";
    }

    /** A resolved static reference (enum variant, static field, or primitive constant). */
    private JavaIr staticRefIr(String name, Type type) {
        if (type.base() == Base.ENUM && program.enumDecl(type.name()) != null) return JavaIr.staticField(program.enumName(type.name()), name, type);
        if (type.base() == Base.STRUCT && program.structDecl(type.name()) != null) return JavaIr.staticField(program.structName(type.name()), fieldName(name), type);
        if (name.equals("MAX_VALUE") || name.equals("MIN_VALUE")) {
            return switch (type.base()) {
                case BYTE -> JavaIr.staticField("Byte", name, type);
                case SHORT -> JavaIr.staticField("Short", name, type);
                case INTEGER -> JavaIr.staticField("Integer", name, type);
                case LONG -> JavaIr.staticField("Long", name, type);
                case FLOAT -> name.equals("MAX_VALUE") ? JavaIr.staticField("Float", name, type) : JavaIr.prefix("-", JavaIr.staticField("Float", "MAX_VALUE", type), type);
                case DOUBLE -> name.equals("MAX_VALUE") ? JavaIr.staticField("Double", name, type) : JavaIr.prefix("-", JavaIr.staticField("Double", "MAX_VALUE", type), type);
                default -> JavaIr.literal("0", type);
            };
        }
        if (type.base() == Base.FLOAT || type.base() == Base.DOUBLE) {
            String owner = type.base() == Base.FLOAT ? "Float" : "Double";
            return switch (name) {
                case "NaN", "POSITIVE_INFINITY", "NEGATIVE_INFINITY" -> JavaIr.staticField(owner, name, type);
                default -> JavaIr.staticField(generatedTypeName(type), name, type);
            };
        }
        return JavaIr.staticField(generatedTypeName(type), name, type);
    }

    /** Lowers a builtin call to Java expression IR, recording the runtime feature it needs. */
    private JavaIr javaBuiltin(SolvikIr.Call c) {
        String builtin = c.builtin();
        List<JavaIr> args = lowerAll(c.arguments());
        if (builtin.startsWith("enum:")) {
            String variant = builtin.substring(5);
            String typeArgs = c.receiverType() == null || c.receiverType().args().isEmpty() ? "" : "<" + joinTypes(c.receiverType().args()) + ">";
            return new JavaIr.StaticCall(generatedTypeName(c.receiverType()), typeArgs, variant, args, c.type());
        }
        if (builtin.startsWith("object:")) {
            JavaIr receiver = checkedReceiver(c.receiver());
            return switch (builtin.substring(7)) {
                case "toString" -> JavaIr.call("RT.format", List.of(receiver), c.type());
                case "hashCode" -> JavaIr.cast("long", JavaIr.call("RT.hash", List.of(receiver), c.type()), c.type());
                default -> JavaIr.call("RT.eq", List.of(receiver, args.get(0)), c.type());
            };
        }
        if (builtin.startsWith("dynamic:")) {
            program.require(RuntimeFeature.DYNAMIC);
            List<JavaIr> all = new ArrayList<>();
            all.add(checkedReceiver(c.receiver()));
            all.add(JavaIr.literal("\"" + builtin.substring(8) + "\"", named(Base.STRING, "String")));
            all.addAll(args);
            return JavaIr.call("RT.dynamic", all, c.type());
        }
        int dot = builtin.indexOf('.');
        String namespace = dot < 0 ? builtin : builtin.substring(0, dot);
        String method = dot < 0 ? builtin : builtin.substring(dot + 1);
        Type receiverType = c.receiverType();
        if (c.receiver() != null) {
            JavaIr receiver = checkedReceiver(c.receiver());
            if (namespace.equals("String")) return stringBuiltin(receiver, method, args, c.type());
            if (namespace.equals("Process")) return processBuiltin(receiver, method, args, c.type());
            return JavaIr.method(receiver, method, args, c.type());
        }
        Type firstArgument = c.arguments().isEmpty() ? null : c.arguments().get(0).type();
        return staticBuiltin(namespace, method, args, firstArgument, receiverType, c.type());
    }

    private JavaIr processBuiltin(JavaIr receiver, String method, List<JavaIr> args, Type type) {
        String java = switch (method) {
            case "wait" -> "waitFor";
            case "start" -> "start";
            case "stdout" -> "stdout";
            case "stderr" -> "stderr";
            case "stdin" -> "stdin";
            case "exitCode" -> "exitCode";
            default -> methodName(method);
        };
        return JavaIr.method(receiver, java, args, type);
    }

    private JavaIr stringBuiltin(JavaIr receiver, String method, List<JavaIr> args, Type type) {
        return switch (method) {
            case "length" -> JavaIr.cast("long", JavaIr.paren(JavaIr.method(receiver, "codePointCount", List.of(JavaIr.literal("0", named(Base.INTEGER, "Integer")), JavaIr.method(receiver, "length", List.of(), type)), type), type), type);
            case "substring" -> { program.require(RuntimeFeature.STRING_ACCESS); yield JavaIr.call("RT.strSubstring", prepend(receiver, args), type); }
            case "charAt" -> { program.require(RuntimeFeature.STRING_ACCESS); yield JavaIr.call("RT.strCharAt", prepend(receiver, args), type); }
            case "indexOf" -> JavaIr.cast("long", JavaIr.method(receiver, "indexOf", args, type), type);
            case "split" -> {
                program.require(RuntimeFeature.LIST);
                JavaIr pattern = JavaIr.staticMethod("java.util.regex.Pattern", "quote", args, type);
                yield JavaIr.call("RT.list", List.of(JavaIr.cast("Object[]", JavaIr.method(receiver, "split", List.of(pattern), type), type)), type);
            }
            default -> JavaIr.method(receiver, javaStringMethod(method), args, type);
        };
    }

    private JavaIr staticBuiltin(String namespace, String method, List<JavaIr> args, Type firstArgument, Type receiverType, Type result) {
        switch (namespace) {
            case "System":
                return switch (method) {
                    case "getOut" -> JavaIr.call("RT.out", List.of(), result);
                    case "getErr" -> JavaIr.call("RT.err", List.of(), result);
                    case "getIn" -> JavaIr.call("RT.in", List.of(), result);
                    case "getLineSeparator" -> JavaIr.staticMethod("System", "lineSeparator", List.of(), result);
                    case "getCurrentTimeMillis" -> JavaIr.staticMethod("System", "currentTimeMillis", List.of(), result);
                    case "getNanoTime" -> JavaIr.staticMethod("System", "nanoTime", List.of(), result);
                    case "getEnv" -> { if (args.isEmpty()) { program.require(RuntimeFeature.ENV); yield JavaIr.call("RT.getenv", List.of(), result); } yield JavaIr.staticMethod("System", "getenv", args, result); }
                    case "getProperty" -> { program.require(RuntimeFeature.PROPERTIES); yield JavaIr.call("RT.property", args, result); }
                    case "setProperty" -> { program.require(RuntimeFeature.PROPERTIES); yield JavaIr.call("RT.setProperty", args, result); }
                    case "clearProperty" -> { program.require(RuntimeFeature.PROPERTIES); yield JavaIr.call("RT.clearProperty", args, result); }
                    default -> JavaIr.literal("null", result);
                };
            case "Math":
                if (method.equals("abs") && !args.isEmpty() && firstArgument != null) {
                    if (firstArgument.base() == Base.LONG) { program.require(RuntimeFeature.ARITHMETIC); return JavaIr.call("RT.absLong", args, result); }
                    if (firstArgument.integral()) { program.require(RuntimeFeature.ARITHMETIC); return JavaIr.call("RT.absInt", args, result); }
                }
                return JavaIr.staticMethod("Math", method, args, result);
            case "Base64": return base64Builtin(method, args, result);
            case "Hash": { program.require(RuntimeFeature.HASH); return JavaIr.call("RT.digest", prepend(JavaIr.literal("\"" + method.toUpperCase() + "\"", named(Base.STRING, "String")), args), result); }
            case "Json": { program.require(RuntimeFeature.JSON); return JavaIr.call(method.equals("stringify") ? "RT.json" : "RT.parseJson", args, result); }
            case "Time": if (method.equals("sleep")) { program.require(RuntimeFeature.TIME); return JavaIr.call("RT.sleep", args, result); } return JavaIr.staticMethod("System", "currentTimeMillis", List.of(), result);
            case "Random":
                program.require(RuntimeFeature.RANDOM);
                return JavaIr.call(method.equals("seed") ? "RT.seedRandom" : method.equals("nextDouble") ? "RT.randomDouble" : "RT.randomLong", args, result);
            case "Type": { program.require(RuntimeFeature.TYPE); return JavaIr.call(method.equals("of") ? "RT.typeOf" : "RT.isType", args, result); }
            case "File":
                program.require(RuntimeFeature.FILE);
                return JavaIr.call(switch (method) { case "read" -> "RT.fileRead"; case "write" -> "RT.fileWrite"; case "exists" -> "RT.fileExists"; case "delete" -> "RT.fileDelete"; case "listDir" -> "RT.fileList"; default -> "RT.fileRead"; }, args, result);
            case "Test": { program.require(RuntimeFeature.TEST); return JavaIr.call(method.equals("assert") ? "RT.assertValue" : "RT.assertEqual", args, result); }
            case "Regex": { program.require(RuntimeFeature.REGEX); return new JavaIr.New("RT.Regex", "", args, result); }
            case "Exception": return new JavaIr.New("RT.ExceptionValue", "", args, result);
            case "Thread": { program.require(RuntimeFeature.THREAD); return new JavaIr.New("RT.ThreadValue", "", args, result); }
            case "Mutex": { program.require(RuntimeFeature.MUTEX); return new JavaIr.New("RT.MutexValue", "", List.of(), result); }
            case "Semaphore": { program.require(RuntimeFeature.SEMAPHORE); return new JavaIr.New("RT.SemaphoreValue", "", args, result); }
            case "Process": { program.require(RuntimeFeature.PROCESS); return new JavaIr.New("RT.ProcessValue", "", args, result); }
            default:
                if (collectionNamespace(namespace)) return collectionConstructor(namespace, result, args);
                if (conversionNamespace(namespace) && method.equals("from")) return conversion(namespace, args, result);
                return JavaIr.literal("null", result);
        }
    }

    private JavaIr base64Builtin(String method, List<JavaIr> args, Type result) {
        JavaIr charset = JavaIr.staticField("StandardCharsets", "UTF_8", named(Base.OBJECT, "Object"));
        if (method.equals("encode")) {
            JavaIr bytes = JavaIr.method(args.get(0), "getBytes", List.of(charset), result);
            JavaIr encoder = JavaIr.method(JavaIr.staticMethod("Base64", "getEncoder", List.of(), result), "encodeToString", List.of(bytes), result);
            return encoder;
        }
        JavaIr decoded = JavaIr.method(JavaIr.staticMethod("Base64", "getDecoder", List.of(), result), "decode", args, result);
        return new JavaIr.New("String", "", List.of(decoded, charset), result);
    }

    private JavaIr javaCall(SolvikIr.Call call) {
        if (call.staticOwnerType() != null && call.name().equals("new")) {
            int[] fieldToParameter = program.trivialFactory(call.staticOwnerType().name());
            if (fieldToParameter != null) return javaStaticConstructor(call, fieldToParameter);
        }
        List<JavaIr> args = lowerAll(call.arguments());
        if (call.staticOwnerType() != null) {
            String typeArguments = call.staticTypeArguments() ? "<" + joinTypes(call.receiverType().args()) + ">" : "";
            return new JavaIr.StaticCall(generatedTypeName(call.staticOwnerType()), typeArguments, methodName(call.name()), args, call.type());
        }
        if (call.receiver() != null) return JavaIr.method(checkedReceiver(call.receiver()), methodName(call.name()), args, call.type());
        if (call.implicitInstance()) return JavaIr.method(new JavaIr.This(call.type()), methodName(call.name()), args, call.type());
        return new JavaIr.StaticCall(program.structName(call.implicitOwner()), "", methodName(call.name()), args, call.type());
    }

    private JavaIr javaStaticConstructor(SolvikIr.Call call, int[] fieldToParameter) {
        String typeArguments = call.staticTypeArguments() ? "<" + joinTypes(call.receiverType().args()) + ">" : "";
        List<JavaIr> args = new ArrayList<>(fieldToParameter.length);
        for (int field = 0; field < fieldToParameter.length; field++) args.add(lower(call.arguments().get(fieldToParameter[field])));
        return new JavaIr.New(program.structName(call.staticOwnerType().name()), typeArguments, args, call.type());
    }

    private JavaIr collectionConstructor(String namespace, Type receiverType, List<JavaIr> args) {
        RuntimeFeature feature = switch (namespace) { case "List" -> RuntimeFeature.LIST; case "Map" -> RuntimeFeature.MAP; case "Stack" -> RuntimeFeature.STACK; default -> RuntimeFeature.SET; };
        program.require(feature);
        String java = switch (namespace) { case "List" -> "RT.SList"; case "Map" -> "RT.SMap"; case "Stack" -> "RT.SStack"; default -> "RT.SSet"; };
        return new JavaIr.New(java + "<>", "", args, receiverType == null ? named(Base.OBJECT, "Object") : receiverType);
    }

    private JavaIr conversion(String namespace, List<JavaIr> args, Type result) {
        if (args.size() == 1) {
            JavaIr value = args.get(0);
            Type source = value.type();
            if (source != null && !source.nullable()) {
                if (namespace.equals("BigInteger") && source.integral()) {
                    if (value instanceof JavaIr.Literal l && l.type() != null && l.type().base() == Base.INTEGER) {
                        try {
                            return JavaIr.staticMethod("BigInteger", "valueOf", List.of(JavaIr.literal(new BigInteger(cleanLiteral(l.java())).longValueExact() + "L", result)), result);
                        } catch (RuntimeException e) { /* fall through */ }
                    }
                    return JavaIr.staticMethod("BigInteger", "valueOf", List.of(value), result);
                }
                Base target = switch (namespace) {
                    case "Double" -> Base.DOUBLE; case "Float" -> Base.FLOAT; case "Long" -> Base.LONG;
                    case "Integer" -> Base.INTEGER; case "Short" -> Base.SHORT; case "Byte" -> Base.BYTE;
                    case "Boolean" -> Base.BOOLEAN; case "String" -> Base.STRING;
                    case "BigInteger" -> Base.BIG_INTEGER; case "BigDecimal" -> Base.BIG_DECIMAL;
                    default -> null;
                };
                if (target != null && conversionAccepts(source, target)) {
                    return source.base() == target ? value : JavaIr.cast(primitiveCast(target), value, result);
                }
            }
        }
        program.require(RuntimeFeature.CONVERSIONS);
        String helper = switch (namespace) {
            case "Long" -> "RT.convertLong"; case "Integer" -> "RT.convertInt"; case "Short" -> "RT.convertShort";
            case "Byte" -> "RT.convertByte"; case "Double" -> "RT.convertDouble"; case "Float" -> "RT.convertFloat";
            case "Boolean" -> "RT.convertBoolean"; case "Char" -> "RT.convertChar";
            case "BigInteger" -> "RT.convertBigInteger"; case "BigDecimal" -> "RT.convertBigDecimal";
            default -> "RT.convertString";
        };
        return JavaIr.call(helper, args, result);
    }

    private static String cleanLiteral(String java) {
        String s = java;
        if (s.endsWith("L") || s.endsWith("l")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private boolean conversionAccepts(Type source, Base target) {
        Base s = source.base();
        return switch (target) {
            case DOUBLE, FLOAT -> source.floating() || source.integral();
            case LONG -> source.integral();
            case INTEGER -> s == Base.BYTE || s == Base.SHORT || s == Base.INTEGER;
            case SHORT -> s == Base.BYTE || s == Base.SHORT;
            case BYTE -> s == Base.BYTE;
            case BOOLEAN -> s == Base.BOOLEAN;
            case STRING -> s == Base.STRING;
            case BIG_INTEGER -> s == Base.BIG_INTEGER;
            case BIG_DECIMAL -> s == Base.BIG_DECIMAL;
            default -> false;
        };
    }

    private boolean collectionNamespace(String namespace) { return namespace.equals("List") || namespace.equals("Map") || namespace.equals("Stack") || namespace.equals("Set"); }
    private boolean conversionNamespace(String namespace) { return namespace.equals("Byte") || namespace.equals("Short") || namespace.equals("Integer") || namespace.equals("Long") || namespace.equals("Float") || namespace.equals("Double") || namespace.equals("BigInteger") || namespace.equals("BigDecimal") || namespace.equals("String") || namespace.equals("Boolean") || namespace.equals("Char"); }

    private String primitiveCast(Base target) {
        return switch (target) {
            case DOUBLE -> "double"; case FLOAT -> "float"; case LONG -> "long"; case INTEGER -> "int";
            case SHORT -> "short"; case BYTE -> "byte"; default -> "";
        };
    }

    private String javaStringMethod(String method) {
        return switch (method) {
            case "startsWith" -> "startsWith"; case "endsWith" -> "endsWith"; case "contains" -> "contains";
            case "replace" -> "replace"; case "trim" -> "trim"; case "toUpperCase" -> "toUpperCase"; case "toLowerCase" -> "toLowerCase";
            default -> method;
        };
    }

    /** Mangles a Solvik method name into a legal, collision-free Java name. */
    public String methodName(String name) {
        if (name.equals("new")) return "__new";
        return JAVA_KEYWORDS.contains(name) ? "__" + name : name;
    }

    /** Mangles a Solvik field name. */
    public String fieldName(String name) { return "f_" + name; }

    /** Mangles a Solvik local name; compiler-generated names pass through. */
    public String localName(String name) { return name.startsWith("__") ? name : "v_" + name; }

    private String literalJava(LiteralKind kind, String text, Type type) {
        return switch (kind) {
            case STRING, CHAR -> javaStringLiteral(text);
            case BOOLEAN -> text;
            case NULL -> "null";
            case INT -> integerLiteral(text, type);
            case REAL -> realLiteral(text);
        };
    }

    private String integerLiteral(String text, Type expected) {
        String clean = Literals.cleanInteger(text);
        Base b = expected.base();
        if (b == Base.BIG_INTEGER) return "new BigInteger(\"" + clean + "\")";
        if (b == Base.LONG) return clean + "L";
        if (b == Base.SHORT) return "(short)" + clean;
        if (b == Base.BYTE) return "(byte)" + clean;
        return clean;
    }

    private String realLiteral(String text) {
        String clean = text.replace("_", "");
        String upper = clean.toUpperCase();
        if (upper.endsWith("BD")) return "new BigDecimal(\"" + clean.substring(0, clean.length() - 2) + "\")";
        if (upper.endsWith("F")) return clean.substring(0, clean.length() - 1) + "f";
        if (upper.endsWith("D")) return clean.substring(0, clean.length() - 1) + "d";
        return clean;
    }

    private String javaStringLiteral(String value) {
        StringBuilder s = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> s.append("\\\\");
                case '"' -> s.append("\\\"");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                case '\t' -> s.append("\\t");
                case '\0' -> s.append("\\0");
                default -> { if (c < 0x20) s.append(String.format("\\u%04x", (int) c)); else s.append(c); }
            }
        }
        return s.append('"').toString();
    }

    private static final java.util.Set<String> JAVA_KEYWORDS = java.util.Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_", "yield",
            "record", "sealed", "permits", "var", "when");

    private String generatedTypeName(Type type) { return program.generatedTypeName(type); }

    private String joinTypes(List<Type> types) {
        StringBuilder builder = new StringBuilder();
        for (Type type : types) { if (builder.length() > 0) builder.append(", "); builder.append(javaType(type, true)); }
        return builder.toString();
    }

    private List<JavaIr> prepend(JavaIr first, List<JavaIr> rest) {
        List<JavaIr> result = new ArrayList<>(rest.size() + 1);
        result.add(first);
        result.addAll(rest);
        return result;
    }

    /** Solvik type to Java representation, recording any runtime type spelling it needs. */
    public String javaType(Type type) { return javaType(type, false); }

    public String javaType(Type type, boolean boxed) {
        if (type == null) return "Object";
        if (type.base() == Base.TYPE_VAR) return type.name() == null ? "Object" : type.name();
        boolean b = boxed || type.nullable();
        String primitive = switch (type.base()) {
            case BOOLEAN -> b ? "Boolean" : "boolean"; case BYTE -> b ? "Byte" : "byte";
            case SHORT -> b ? "Short" : "short"; case INTEGER -> b ? "Integer" : "int";
            case LONG -> b ? "Long" : "long"; case FLOAT -> b ? "Float" : "float";
            case DOUBLE -> b ? "Double" : "double"; case CHAR -> "String"; default -> null;
        };
        if (primitive != null) return primitive;
        String base = switch (type.base()) {
            case BIG_INTEGER -> "BigInteger"; case BIG_DECIMAL -> "BigDecimal"; case STRING -> "String";
            case OBJECT, THROWABLE -> "Object"; case VOID -> "void"; case NULL -> "Object";
            case LIST -> { program.require(RuntimeFeature.LIST); yield "RT.SList"; }
            case MAP -> { program.require(RuntimeFeature.MAP); yield "RT.SMap"; }
            case STACK -> { program.require(RuntimeFeature.STACK); yield "RT.SStack"; }
            case SET -> { program.require(RuntimeFeature.SET); yield "RT.SSet"; }
            case STRUCT -> program.structName(type.name());
            case TRAIT -> program.traitName(type.name());
            case ENUM -> program.enumName(type.name());
            case WRITER -> "RT.Writer"; case READER -> "RT.Reader";
            case THREAD -> { program.require(RuntimeFeature.THREAD); yield "RT.ThreadValue"; }
            case MUTEX -> { program.require(RuntimeFeature.MUTEX); yield "RT.MutexValue"; }
            case SEMAPHORE -> { program.require(RuntimeFeature.SEMAPHORE); yield "RT.SemaphoreValue"; }
            case PROCESS -> { program.require(RuntimeFeature.PROCESS); yield "RT.ProcessValue"; }
            case REGEX -> { program.require(RuntimeFeature.REGEX); yield "RT.Regex"; }
            case EXCEPTION -> "RT.ExceptionValue"; case RUNNABLE -> { program.require(RuntimeFeature.THREAD); yield "RT.RunnableLike"; }
            case RANGE -> "Iterable<Long>";
            default -> "Object";
        };
        if (!type.args().isEmpty()) return base + "<" + joinTypes(type.args()) + ">";
        return base;
    }

    private String typeArgumentsOf(List<SolvikProgram.TypeParameter> params) {
        if (params.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("<");
        for (int i = 0; i < params.size(); i++) { if (i > 0) builder.append(", "); builder.append(params.get(i).name()); }
        return builder.append(">").toString();
    }

    /**
     * The declared Java type for a match subject, or null when the subject must
     * stay {@code Object}. Keeping a concrete static type lets literal, list, and
     * enum-variant patterns lower to direct Java comparisons instead of boxing to
     * Object and routing through {@code RT.eq}.
     */
    String matchSubjectJavaType(Type type) {
        if (type == null || type.nullable()) return null;
        return switch (type.base()) {
            case BOOLEAN, BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, STRING, CHAR -> javaType(type, true);
            case ENUM -> {
                SolvikProgram.Enum declaration = program.enumDecl(type.name());
                yield declaration != null && !declaration.typeParameters().isEmpty() && type.args().isEmpty() ? null : javaType(type, true);
            }
            case LIST -> type.args().isEmpty() ? null : javaType(type, true);
            default -> null;
        };
    }

    private String javaMatch(SolvikIr.Match match) {
        Type result = match.type();
        Type subjectType = match.subject().type();
        boolean typed = matchSubjectJavaType(subjectType) != null;
        if (javaSimpleSubject(match.subject()) && match.arms().stream().noneMatch(a -> javaPatternHasBinding(a.pattern()))) {
            String subject = emit(match.subject());
            String value = "RT.<" + javaType(result, true) + ">noMatch()";
            for (int i = match.arms().size() - 1; i >= 0; i--) {
                SolvikIr.MatchArm arm = match.arms().get(i);
                String condition = javaPatternCondition(arm.pattern(), subject, subjectType, typed);
                value = condition.equals("true") ? emit(arm.body()) : "(" + condition + " ? " + emit(arm.body()) + " : " + value + ")";
            }
            return value;
        }
        String subject = "__match" + temp++;
        String declared = typed ? matchSubjectJavaType(subjectType) : "Object";
        StringBuilder s = new StringBuilder("((java.util.function.Supplier<" + javaType(result, true) + ">) () -> { " + declared + " " + subject + " = " + emit(match.subject()) + "; ");
        boolean first = true;
        boolean exhaustive = false;
        for (SolvikIr.MatchArm arm : match.arms()) {
            String condition = javaPatternCondition(arm.pattern(), subject, subjectType, typed);
            String bindings = javaPatternBindingsInline(arm.pattern(), subject, subjectType, typed);
            if (condition.equals("true")) { s.append(first ? "{ " : "else { "); exhaustive = true; }
            else s.append(first ? "if (" : "else if (").append(unparenthesize(condition)).append(") { ");
            s.append(bindings).append("return ").append(emit(arm.body())).append("; } ");
            first = false;
            if (exhaustive) break;
        }
        if (!exhaustive) s.append("throw new RT.Thrown(new RT.ExceptionValue(\"non-exhaustive match\")); ");
        s.append("}).get()");
        return s.toString();
    }

    private int temp;

    boolean javaSimpleSubject(SolvikIr expression) {
        return expression instanceof SolvikIr.Local || expression instanceof SolvikIr.Field
                || expression instanceof SolvikIr.Self || expression instanceof SolvikIr.Literal
                || expression instanceof SolvikIr.StaticRef;
    }

    boolean javaPatternHasBinding(SolvikIr.SolvikPattern pattern) {
        if (pattern instanceof SolvikIr.SolvikPattern.Bind) return true;
        if (pattern instanceof SolvikIr.SolvikPattern.ListPattern p) return p.elements().stream().anyMatch(this::javaPatternHasBinding);
        if (pattern instanceof SolvikIr.SolvikPattern.Variant p) return p.payload().stream().anyMatch(this::javaPatternHasBinding);
        return false;
    }

    String javaPatternCondition(SolvikIr.SolvikPattern pattern, String subject, Type subjectType, boolean typed) {
        if (pattern instanceof SolvikIr.SolvikPattern.Wildcard || pattern instanceof SolvikIr.SolvikPattern.Bind) return "true";
        if (pattern instanceof SolvikIr.SolvikPattern.Literal p) {
            String literal = emit(p.value());
            if (typed && (subjectType.base() == Base.STRING || subjectType.base() == Base.CHAR)) return subject + ".equals(" + literal + ")";
            if (typed && subjectType.primitive()) return "(" + subject + " == " + literal + ")";
            return "RT.eq(" + subject + ", " + literal + ")";
        }
        if (pattern instanceof SolvikIr.SolvikPattern.ListPattern p) return javaListCondition(p.elements(), subject, p.elementType(), typed);
        if (pattern instanceof SolvikIr.SolvikPattern.Variant p) {
            String javaEnum = program.enumName(p.enumName());
            SolvikProgram.Enum declaration = program.enumDecl(p.enumName());
            String wildcard = declaration != null && !declaration.typeParameters().isEmpty() ? "<?>" : "";
            boolean variantTyped = typed && subjectType != null && subjectType.base() == Base.ENUM && p.enumName().equals(subjectType.name());
            String receiver = variantTyped ? subject : "((" + javaEnum + wildcard + ")" + subject + ")";
            String condition = variantTyped ? "(" + subject + ".tag()==" + p.tag() + ")" : "(" + subject + " instanceof " + javaEnum + wildcard + " && " + receiver + ".tag()==" + p.tag() + ")";
            if (declaration != null && !p.payload().isEmpty()) {
                String payloadExpression = receiver + ".payload()";
                String nested = p.payload().size() == 1
                        ? javaPatternCondition(p.payload().get(0), payloadExpression, p.payloadType(), false)
                        : javaListCondition(p.payload(), payloadExpression, p.payloadType(), false);
                if (!nested.equals("true")) condition = "(" + condition + " && " + nested + ")";
            }
            return condition;
        }
        return "false";
    }

    String javaListCondition(List<SolvikIr.SolvikPattern> elements, String subject, Type elementType, boolean listTyped) {
        String access = listTyped ? subject : "((RT.SList<?>)" + subject + ")";
        boolean elementTyped = listTyped && matchSubjectJavaType(elementType) != null;
        StringBuilder c = new StringBuilder("(" + (listTyped ? "" : subject + " instanceof RT.SList<?> && ") + access + ".size()==" + elements.size());
        for (int i = 0; i < elements.size(); i++) {
            String nested = javaPatternCondition(elements.get(i), access + ".get(" + i + ")", elementType, elementTyped);
            if (!nested.equals("true")) c.append(" && ").append(nested);
        }
        return c.append(")").toString();
    }

    static String unparenthesize(String text) {
        if (text.length() < 2 || text.charAt(0) != '(') return text;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i == text.length() - 1 ? text.substring(1, i) : text;
        }
        return text;
    }

    String javaPatternBindingsInline(SolvikIr.SolvikPattern pattern, String subject, Type subjectType, boolean typed) {
        return String.join(" ", javaPatternBindings(pattern, subject, subjectType, typed));
    }

    List<String> javaPatternBindings(SolvikIr.SolvikPattern pattern, String subject, Type subjectType, boolean typed) {
        List<String> result = new ArrayList<>();
        if (pattern instanceof SolvikIr.SolvikPattern.Bind b) {
            result.add(javaType(subjectType, true) + " " + localName(b.name()) + " = " + (typed ? subject : castValue(subject, subjectType)) + ";");
        } else if (pattern instanceof SolvikIr.SolvikPattern.ListPattern p) {
            boolean elementTyped = typed && matchSubjectJavaType(p.elementType()) != null;
            String access = typed ? subject : "((RT.SList<?>)" + subject + ")";
            for (int i = 0; i < p.elements().size(); i++) result.addAll(javaPatternBindings(p.elements().get(i), access + ".get(" + i + ")", p.elementType(), elementTyped));
        } else if (pattern instanceof SolvikIr.SolvikPattern.Variant p) {
            String javaEnum = program.enumName(p.enumName());
            SolvikProgram.Enum declaration = program.enumDecl(p.enumName());
            String wildcard = declaration != null && !declaration.typeParameters().isEmpty() ? "<?>" : "";
            boolean variantTyped = typed && subjectType != null && subjectType.base() == Base.ENUM && p.enumName().equals(subjectType.name());
            String receiver = variantTyped ? subject : "((" + javaEnum + wildcard + ")" + subject + ")";
            for (SolvikIr.SolvikPattern x : p.payload()) result.addAll(javaPatternBindings(x, receiver + ".payload()", p.payloadType(), false));
        }
        return result;
    }

    String castValue(String expression, Type t) {
        if (t.primitive() && !t.nullable()) return switch (t.base()) {
            case LONG -> "((Long)" + expression + ").longValue()";
            case INTEGER -> "((Integer)" + expression + ").intValue()";
            case SHORT -> "((Short)" + expression + ").shortValue()";
            case BYTE -> "((Byte)" + expression + ").byteValue()";
            case FLOAT -> "((Float)" + expression + ").floatValue()";
            case DOUBLE -> "((Double)" + expression + ").doubleValue()";
            case BOOLEAN -> "((Boolean)" + expression + ").booleanValue()";
            default -> expression;
        };
        if (t.base() == Base.OBJECT || t.base() == Base.THROWABLE) return expression;
        return "(" + javaType(t, true) + ")" + expression;
    }

    /** Renders the equality test used by a non-constant switch branch chain. */
    String switchEquality(String subject, Type subjectType, SolvikIr value) {
        JavaIr left = new JavaIr.Identifier(subject, subjectType);
        JavaIr right = lower(value);
        Type result = named(Base.BOOLEAN, "Boolean");
        return JavaIr.render(optimizer.optimize(equality(BinaryOp.EQ, false, false, left, right, subjectType, value.type(), result)));
    }
    List<List<Long>> constantIntegerCases(SolvikStmt.Switch s) {
        Type subjectType = s.subjectType();
        if (subjectType == null || subjectType.nullable() || subjectType.base() != Base.INTEGER) return null;
        for (SolvikStmt.SwitchCase c : s.cases()) if (hasLoopEscape(c.body())) return null;
        List<List<Long>> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int defaults = 0;
        for (SolvikStmt.SwitchCase c : s.cases()) {
            if (c.isDefault()) { if (++defaults > 1) return null; result.add(List.of()); continue; }
            List<Long> caseValues = new ArrayList<>(c.values().size());
            for (SolvikIr value : c.values()) {
                Long constant = constantCaseValue(value);
                if (constant == null || !seen.add(constant)) return null;
                caseValues.add(constant);
            }
            result.add(caseValues);
        }
        return result;
    }

    Long constantCaseValue(SolvikIr expression) {
        if (expression instanceof SolvikIr.Coerce c) expression = c.value();
        if (!(expression instanceof SolvikIr.Literal l) || l.kind() != LiteralKind.INT) return null;
        try {
            BigInteger value = new BigInteger(Literals.cleanInteger(l.text()));
            if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) return null;
            return value.longValue();
        } catch (RuntimeException e) { return null; }
    }

    boolean blockDiverges(List<SolvikStmt> statements) {
        return !statements.isEmpty() && stmtDiverges(statements.get(statements.size() - 1));
    }

    boolean hasLoopEscape(List<SolvikStmt> statements) {
        for (SolvikStmt statement : statements) {
            if (statement instanceof SolvikStmt.Break || statement instanceof SolvikStmt.Continue) return true;
            if (statement instanceof SolvikStmt.Block b) { if (hasLoopEscape(b.statements())) return true; }
            else if (statement instanceof SolvikStmt.If i) { if (hasLoopEscape(i.thenBranch()) || hasLoopEscape(i.elseBranch())) return true; }
            else if (statement instanceof SolvikStmt.Try t) {
                if (hasLoopEscape(t.body())) return true;
                for (SolvikStmt.Catch c : t.catches()) if (hasLoopEscape(c.body())) return true;
                if (t.finallyBlock() != null && hasLoopEscape(t.finallyBlock())) return true;
            }
            // While/ForRange/ForEach bodies bind their own break/continue.
        }
        return false;
    }

    boolean stmtDiverges(SolvikStmt statement) {
        if (statement instanceof SolvikStmt.Return || statement instanceof SolvikStmt.Throw || statement instanceof SolvikStmt.Break || statement instanceof SolvikStmt.Continue) return true;
        if (statement instanceof SolvikStmt.Block b) return blockDiverges(b.statements());
        if (statement instanceof SolvikStmt.If i) return blockDiverges(i.thenBranch()) && !i.elseBranch().isEmpty() && blockDiverges(i.elseBranch());
        return false;
    }
}
