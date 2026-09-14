package org.solvik.transpiler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.solvik.transpiler.Ast.BinaryOp;
import static org.solvik.transpiler.Ast.LiteralKind;
import static org.solvik.transpiler.Ast.UnaryOp;
import static org.solvik.transpiler.TypeModel.Base;

/**
 * Small, conservative optimizer over the typed Solvik IR.
 *
 * <p>This pass runs after semantic analysis has completed, so every node is
 * already resolved and diagnostics have been emitted. It therefore cannot hide
 * compile-time errors. It performs only transformations that are exact for
 * Solvik semantics:</p>
 *
 * <ul>
 *   <li>integer constant folding for {@code + - * / %} and unary negation,
 *       leaving expressions that would overflow or divide by zero untouched so
 *       the generated code still raises the runtime error;</li>
 *   <li>constant integer and string/char comparisons;</li>
 *   <li>boolean {@code !}/short-circuit simplification;</li>
 *   <li>{@code ??} simplification with a literal left operand;</li>
 *   <li>constant {@code if}/{@code while} condition simplification.</li>
 * </ul>
 *
 * <p>It deliberately does not attempt floating-point rewriting (javac already
 * folds literal Java arithmetic), constant propagation, or any global
 * data-flow analysis. Nodes are reused unchanged whenever a fold does not
 * apply, so sources without foldable expressions pay only a single linear walk
 * and almost no allocation.</p>
 */
public final class IrOptimizer {
    public SolvikProgram optimize(SolvikProgram program) {
        List<SolvikProgram.Trait> traits = null;
        for (int i = 0; i < program.traits().size(); i++) {
            SolvikProgram.Trait declaration = program.traits().get(i);
            List<SolvikProgram.Method> methods = optimizeMethods(declaration.methods());
            if (methods == declaration.methods()) { if (traits != null) traits.add(declaration); continue; }
            if (traits == null) traits = new ArrayList<>(program.traits().subList(0, i));
            traits.add(new SolvikProgram.Trait(declaration.name(), declaration.typeParameters(),
                    declaration.extendsTypes(), methods));
        }
        List<SolvikProgram.Struct> structs = null;
        for (int i = 0; i < program.structs().size(); i++) {
            SolvikProgram.Struct declaration = program.structs().get(i);
            List<SolvikProgram.Field> fields = optimizeFields(declaration.fields());
            List<SolvikProgram.Method> methods = optimizeMethods(declaration.methods());
            List<SolvikStmt> staticBlock = declaration.staticBlock() == null ? null : optimizeStatements(declaration.staticBlock());
            if (fields == declaration.fields() && methods == declaration.methods() && staticBlock == declaration.staticBlock()) {
                if (structs != null) structs.add(declaration);
                continue;
            }
            if (structs == null) structs = new ArrayList<>(program.structs().subList(0, i));
            structs.add(new SolvikProgram.Struct(declaration.name(), declaration.typeParameters(), fields, methods,
                    staticBlock, declaration.implementsTypes(), declaration.index()));
        }
        List<SolvikProgram.Trait> finalTraits = traits == null ? program.traits() : traits;
        List<SolvikProgram.Struct> finalStructs = structs == null ? program.structs() : structs;
        if (finalTraits == program.traits() && finalStructs == program.structs()) return program;
        return new SolvikProgram(finalTraits, program.enums(), finalStructs);
    }

    private List<SolvikProgram.Field> optimizeFields(List<SolvikProgram.Field> fields) {
        List<SolvikProgram.Field> result = null;
        for (int i = 0; i < fields.size(); i++) {
            SolvikProgram.Field field = fields.get(i);
            if (field.initializer() == null) { if (result != null) result.add(field); continue; }
            SolvikIr initializer = optimizeExpression(field.initializer());
            if (initializer == field.initializer()) { if (result != null) result.add(field); continue; }
            if (result == null) result = new ArrayList<>(fields.subList(0, i));
            result.add(new SolvikProgram.Field(field.name(), field.type(), field.mutable(), field.isStatic(), initializer));
        }
        return result == null ? fields : result;
    }

    private List<SolvikProgram.Method> optimizeMethods(List<SolvikProgram.Method> methods) {
        List<SolvikProgram.Method> result = methods;
        for (int i = 0; i < methods.size(); i++) {
            SolvikProgram.Method method = methods.get(i);
            if (method.body() == null) continue;
            List<SolvikStmt> body = optimizeStatements(method.body());
            if (body == method.body()) continue;
            if (result == methods) result = new ArrayList<>(methods);
            result.set(i, new SolvikProgram.Method(method.name(), method.params(), method.returnType(), method.returnBoxed(),
                    method.isPublic(), method.instance(), method.typeParameters(), body));
        }
        return result;
    }

    private List<SolvikStmt> optimizeStatements(List<SolvikStmt> statements) {
        List<SolvikStmt> result = null;
        for (int i = 0; i < statements.size(); i++) {
            SolvikStmt original = statements.get(i);
            SolvikStmt optimized = optimizeStatement(original);
            if (optimized != original && result == null) result = new ArrayList<>(statements.subList(0, i));
            if (result != null) result.add(optimized);
        }
        return result == null ? statements : result;
    }

    private SolvikStmt optimizeStatement(SolvikStmt statement) {
        if (statement instanceof SolvikStmt.VarDecl d) {
            if (d.initializer() == null) return d;
            SolvikIr initializer = optimizeExpression(d.initializer());
            return initializer == d.initializer() ? d : new SolvikStmt.VarDecl(d.name(), d.type(), initializer);
        }
        if (statement instanceof SolvikStmt.Expr e) {
            SolvikIr expression = optimizeExpression(e.expression());
            return expression == e.expression() ? e : new SolvikStmt.Expr(expression);
        }
        if (statement instanceof SolvikStmt.Return r) {
            if (r.value() == null) return r;
            SolvikIr value = optimizeExpression(r.value());
            return value == r.value() ? r : new SolvikStmt.Return(value);
        }
        if (statement instanceof SolvikStmt.Throw t) {
            SolvikIr value = optimizeExpression(t.value());
            return value == t.value() ? t : new SolvikStmt.Throw(value);
        }
        if (statement instanceof SolvikStmt.If i) {
            SolvikIr condition = optimizeExpression(i.condition());
            Boolean chosen = constantBoolean(condition);
            List<SolvikStmt> thenBranch = optimizeStatements(i.thenBranch());
            List<SolvikStmt> elseBranch = optimizeStatements(i.elseBranch());
            if (chosen != null) return new SolvikStmt.Block(chosen == Boolean.TRUE ? thenBranch : elseBranch);
            return condition == i.condition() && thenBranch == i.thenBranch() && elseBranch == i.elseBranch()
                    ? i : new SolvikStmt.If(condition, thenBranch, elseBranch);
        }
        if (statement instanceof SolvikStmt.While w) {
            SolvikIr condition = optimizeExpression(w.condition());
            if (Boolean.FALSE.equals(constantBoolean(condition))) return new SolvikStmt.Block(List.of());
            List<SolvikStmt> body = optimizeStatements(w.body());
            return condition == w.condition() && body == w.body() ? w : new SolvikStmt.While(condition, body);
        }
        if (statement instanceof SolvikStmt.ForRange f) {
            SolvikIr start = optimizeExpression(f.start()), end = optimizeExpression(f.end());
            List<SolvikStmt> body = optimizeStatements(f.body());
            return start == f.start() && end == f.end() && body == f.body() ? f
                    : new SolvikStmt.ForRange(f.name(), f.loopType(), start, end, f.inclusive(), body);
        }
        if (statement instanceof SolvikStmt.ForEach f) {
            SolvikIr iterable = optimizeExpression(f.iterable());
            List<SolvikStmt> body = optimizeStatements(f.body());
            return iterable == f.iterable() && body == f.body() ? f
                    : new SolvikStmt.ForEach(f.name(), f.elementType(), iterable, body);
        }
        if (statement instanceof SolvikStmt.Switch s) {
            SolvikIr subject = optimizeExpression(s.subject());
            List<SolvikStmt.SwitchCase> cases = s.cases();
            List<SolvikStmt.SwitchCase> optimizedCases = null;
            for (int i = 0; i < cases.size(); i++) {
                SolvikStmt.SwitchCase c = cases.get(i);
                List<SolvikIr> values = optimizeAll(c.values());
                List<SolvikStmt> body = optimizeStatements(c.body());
                if (values == c.values() && body == c.body()) { if (optimizedCases != null) optimizedCases.add(c); continue; }
                if (optimizedCases == null) optimizedCases = new ArrayList<>(cases.subList(0, i));
                optimizedCases.add(new SolvikStmt.SwitchCase(c.isDefault(), values, body));
            }
            if (optimizedCases != null) cases = optimizedCases;
            return subject == s.subject() && cases == s.cases() ? s : new SolvikStmt.Switch(subject, s.subjectType(), s.directEquality(), cases);
        }
        if (statement instanceof SolvikStmt.Try t) {
            List<SolvikStmt> body = optimizeStatements(t.body());
            List<SolvikStmt.Catch> catches = t.catches();
            List<SolvikStmt.Catch> optimizedCatches = null;
            for (int i = 0; i < catches.size(); i++) {
                SolvikStmt.Catch c = catches.get(i);
                List<SolvikStmt> catchBody = optimizeStatements(c.body());
                if (catchBody == c.body()) { if (optimizedCatches != null) optimizedCatches.add(c); continue; }
                if (optimizedCatches == null) optimizedCatches = new ArrayList<>(catches.subList(0, i));
                optimizedCatches.add(new SolvikStmt.Catch(c.type(), c.name(), catchBody));
            }
            if (optimizedCatches != null) catches = optimizedCatches;
            List<SolvikStmt> finallyBlock = t.finallyBlock() == null ? null : optimizeStatements(t.finallyBlock());
            return body == t.body() && catches == t.catches() && finallyBlock == t.finallyBlock() ? t
                    : new SolvikStmt.Try(body, catches, finallyBlock);
        }
        if (statement instanceof SolvikStmt.Block b) {
            List<SolvikStmt> statements = optimizeStatements(b.statements());
            return statements == b.statements() ? b : new SolvikStmt.Block(statements);
        }
        if (statement instanceof SolvikStmt.MatchStmt m) {
            SolvikIr subject = optimizeExpression(m.subject());
            List<SolvikIr.MatchArm> arms = m.arms();
            List<SolvikIr.MatchArm> optimizedArms = null;
            for (int i = 0; i < arms.size(); i++) {
                SolvikIr.MatchArm arm = arms.get(i);
                SolvikIr body = optimizeExpression(arm.body());
                if (body == arm.body()) { if (optimizedArms != null) optimizedArms.add(arm); continue; }
                if (optimizedArms == null) optimizedArms = new ArrayList<>(arms.subList(0, i));
                optimizedArms.add(new SolvikIr.MatchArm(arm.pattern(), body));
            }
            if (optimizedArms != null) arms = optimizedArms;
            return subject == m.subject() && arms == m.arms() ? m : new SolvikStmt.MatchStmt(subject, arms);
        }
        return statement;
    }

    private SolvikIr optimizeExpression(SolvikIr expression) {
        if (expression instanceof SolvikIr.Binary b) {
            SolvikIr left = optimizeExpression(b.left()), right = optimizeExpression(b.right());
            SolvikIr folded = foldBinary(b, left, right);
            if (folded != null) return folded;
            return left == b.left() && right == b.right() ? b : new SolvikIr.Binary(b.op(), left, right, b.leftType(), b.rightType(), b.type());
        }
        if (expression instanceof SolvikIr.Unary u) {
            SolvikIr operand = optimizeExpression(u.operand());
            SolvikIr folded = foldUnary(u, operand);
            if (folded != null) return folded;
            return operand == u.operand() ? u : new SolvikIr.Unary(u.op(), operand, u.operandType(), u.type());
        }
        if (expression instanceof SolvikIr.Coalesce c) {
            SolvikIr left = optimizeExpression(c.left()), right = optimizeExpression(c.right());
            SolvikIr folded = foldCoalesce(c, left, right);
            if (folded != null) return folded;
            return left == c.left() && right == c.right() ? c : new SolvikIr.Coalesce(left, right, c.type());
        }
        if (expression instanceof SolvikIr.ListLiteral l) {
            List<SolvikIr> elements = optimizeAll(l.elements());
            return elements == l.elements() ? l : new SolvikIr.ListLiteral(elements, l.elementType(), l.type());
        }
        if (expression instanceof SolvikIr.MapLiteral m) {
            List<java.util.Map.Entry<SolvikIr, SolvikIr>> entries = m.entries();
            List<java.util.Map.Entry<SolvikIr, SolvikIr>> optimized = null;
            for (int i = 0; i < entries.size(); i++) {
                java.util.Map.Entry<SolvikIr, SolvikIr> entry = entries.get(i);
                SolvikIr key = optimizeExpression(entry.getKey()), value = optimizeExpression(entry.getValue());
                if (key == entry.getKey() && value == entry.getValue()) { if (optimized != null) optimized.add(entry); continue; }
                if (optimized == null) optimized = new ArrayList<>(entries.subList(0, i));
                optimized.add(java.util.Map.entry(key, value));
            }
            if (optimized != null) entries = optimized;
            return entries == m.entries() ? m : new SolvikIr.MapLiteral(entries, m.keyType(), m.valueType(), m.type());
        }
        if (expression instanceof SolvikIr.Member m) {
            SolvikIr receiver = optimizeExpression(m.receiver());
            return receiver == m.receiver() ? m : new SolvikIr.Member(receiver, m.name(), m.isField(), m.type());
        }
        if (expression instanceof SolvikIr.Assign a) {
            SolvikIr target = optimizeExpression(a.target()), value = optimizeExpression(a.value());
            return target == a.target() && value == a.value() ? a : new SolvikIr.Assign(target, value, a.type());
        }
        if (expression instanceof SolvikIr.Update u) {
            SolvikIr target = optimizeExpression(u.target()), value = optimizeExpression(u.value());
            return target == u.target() && value == u.value() ? u : new SolvikIr.Update(u.op(), target, value, u.promoted(), u.targetType(), u.valueType());
        }
        if (expression instanceof SolvikIr.Call c) {
            SolvikIr receiver = c.receiver() == null ? null : optimizeExpression(c.receiver());
            List<SolvikIr> arguments = optimizeAll(c.arguments());
            return receiver == c.receiver() && arguments == c.arguments() ? c
                    : new SolvikIr.Call(receiver, c.staticOwnerType(), c.staticTypeArguments(), c.implicitOwner(), c.implicitInstance(), c.name(), c.builtin(), arguments, c.receiverType(), c.type());
        }
        if (expression instanceof SolvikIr.Coerce c) {
            SolvikIr value = optimizeExpression(c.value());
            return value == c.value() ? c : new SolvikIr.Coerce(value, c.from(), c.to(), c.constantInteger());
        }
        if (expression instanceof SolvikIr.SelfInit s) {
            List<SolvikIr> values = optimizeAll(s.values());
            return values == s.values() ? s : new SolvikIr.SelfInit(s.owner(), values, s.type());
        }
        if (expression instanceof SolvikIr.Range r) {
            SolvikIr start = optimizeExpression(r.start()), end = optimizeExpression(r.end());
            return start == r.start() && end == r.end() ? r : new SolvikIr.Range(start, end, r.inclusive(), r.type());
        }
        if (expression instanceof SolvikIr.Match m) {
            SolvikIr subject = optimizeExpression(m.subject());
            List<SolvikIr.MatchArm> arms = m.arms();
            List<SolvikIr.MatchArm> optimized = null;
            for (int i = 0; i < arms.size(); i++) {
                SolvikIr.MatchArm arm = arms.get(i);
                SolvikIr body = optimizeExpression(arm.body());
                if (body == arm.body()) { if (optimized != null) optimized.add(arm); continue; }
                if (optimized == null) optimized = new ArrayList<>(arms.subList(0, i));
                optimized.add(new SolvikIr.MatchArm(arm.pattern(), body));
            }
            if (optimized != null) arms = optimized;
            return subject == m.subject() && arms == m.arms() ? m : new SolvikIr.Match(subject, arms, m.type());
        }
        return expression;
    }

    private List<SolvikIr> optimizeAll(List<SolvikIr> expressions) {
        List<SolvikIr> result = null;
        for (int i = 0; i < expressions.size(); i++) {
            SolvikIr original = expressions.get(i);
            SolvikIr optimized = optimizeExpression(original);
            if (optimized != original && result == null) result = new ArrayList<>(expressions.subList(0, i));
            if (result != null) result.add(optimized);
        }
        return result == null ? expressions : result;
    }

    /** Returns a folded replacement for the unary node, or null when nothing folds. */
    private SolvikIr foldUnary(SolvikIr.Unary u, SolvikIr operand) {
        if (u.op() == UnaryOp.NOT) {
            if (operand instanceof SolvikIr.Unary inner && inner.op() == UnaryOp.NOT) return inner.operand();
            Boolean value = constantBoolean(operand);
            if (value != null) return booleanLiteral(!value, u.type());
            return null;
        }
        BigInteger integer = integerLiteral(operand);
        return integer == null ? null : integerConstant(integer.negate(), u.type());
    }

    /** Returns a folded replacement for the binary node, or null when nothing folds. */
    private SolvikIr foldBinary(SolvikIr.Binary b, SolvikIr left, SolvikIr right) {
        if (b.op() == BinaryOp.AND || b.op() == BinaryOp.OR) return foldLogical(b, left, right);
        if (b.op() == BinaryOp.ADD || b.op() == BinaryOp.SUB || b.op() == BinaryOp.MUL || b.op() == BinaryOp.DIV || b.op() == BinaryOp.MOD) {
            BigInteger a = integerLiteral(left), c = integerLiteral(right);
            if (a == null || c == null) return null;
            // Solvik raises an overflow error for MIN_VALUE / -1 and MIN_VALUE % -1,
            // which Java's BigInteger math would silently reduce. Leave those
            // expressions for the runtime helpers so the error is preserved.
            boolean division = b.op() == BinaryOp.DIV || b.op() == BinaryOp.MOD;
            if (division && c.equals(BigInteger.valueOf(-1))
                    && (a.equals(BigInteger.valueOf(Long.MIN_VALUE)) || a.equals(BigInteger.valueOf(Integer.MIN_VALUE)))) return null;
            BigInteger value = switch (b.op()) {
                case ADD -> a.add(c);
                case SUB -> a.subtract(c);
                case MUL -> a.multiply(c);
                case DIV -> c.signum() == 0 ? null : a.divide(c);
                default -> c.signum() == 0 ? null : a.remainder(c);
            };
            return value == null ? null : integerConstant(value, b.type());
        }
        if (b.op() == BinaryOp.EQ || b.op() == BinaryOp.NE || b.op() == BinaryOp.LT || b.op() == BinaryOp.LE || b.op() == BinaryOp.GT || b.op() == BinaryOp.GE) {
            BigInteger a = integerLiteral(left), c = integerLiteral(right);
            if (a != null && c != null) {
                int compared = a.compareTo(c);
                boolean result = switch (b.op()) {
                    case EQ -> compared == 0; case NE -> compared != 0; case LT -> compared < 0;
                    case LE -> compared <= 0; case GT -> compared > 0; default -> compared >= 0;
                };
                return booleanLiteral(result, b.type());
            }
            // String/char literal equality is exact and side-effect free.
            if ((b.op() == BinaryOp.EQ || b.op() == BinaryOp.NE)
                    && left instanceof SolvikIr.Literal ls && right instanceof SolvikIr.Literal rs
                    && (ls.kind() == LiteralKind.STRING || ls.kind() == LiteralKind.CHAR) && rs.kind() == ls.kind()) {
                return booleanLiteral(ls.text().equals(rs.text()) ^ b.op() == BinaryOp.NE, b.type());
            }
        }
        return null;
    }

    private SolvikIr foldLogical(SolvikIr.Binary b, SolvikIr left, SolvikIr right) {
        Boolean a = constantBoolean(left), c = constantBoolean(right);
        if (b.op() == BinaryOp.AND) {
            // `false && x` never evaluates x under short-circuit semantics, so the
            // operand can be dropped; `x && false` still evaluates x and is kept.
            if (Boolean.FALSE.equals(a)) return booleanLiteral(false, b.type());
            if (Boolean.TRUE.equals(a)) return right;
            if (Boolean.TRUE.equals(c)) return left;
        } else {
            if (Boolean.TRUE.equals(a)) return booleanLiteral(true, b.type());
            if (Boolean.FALSE.equals(a)) return right;
            if (Boolean.FALSE.equals(c)) return left;
        }
        return null;
    }

    private SolvikIr foldCoalesce(SolvikIr.Coalesce c, SolvikIr left, SolvikIr right) {
        if (left instanceof SolvikIr.Literal literal) return literal.kind() == LiteralKind.NULL ? right : left;
        return null;
    }

    /** Returns the constant boolean value of an expression, or null when it is not a literal. */
    private Boolean constantBoolean(SolvikIr expression) {
        if (expression instanceof SolvikIr.Literal literal && literal.kind() == LiteralKind.BOOLEAN) return Boolean.parseBoolean(literal.text());
        return null;
    }

    /** Returns the exact integer value of a literal, or null for non-integer literals. */
    private BigInteger integerLiteral(SolvikIr expression) {
        if (expression instanceof SolvikIr.Literal literal && literal.kind() == LiteralKind.INT) {
            try { return new BigInteger(SemanticAnalyzer.cleanInteger(literal.text())); } catch (RuntimeException e) { return null; }
        }
        return null;
    }

    /**
     * Builds an integer literal of {@code type} when it fits, refusing to fold
     * when the value would overflow the target so the runtime error is preserved.
     */
    private SolvikIr integerConstant(BigInteger value, TypeModel.Type type) {
        BigInteger low, high;
        switch (type.base()) {
            case BYTE -> { low = BigInteger.valueOf(Byte.MIN_VALUE); high = BigInteger.valueOf(Byte.MAX_VALUE); }
            case SHORT -> { low = BigInteger.valueOf(Short.MIN_VALUE); high = BigInteger.valueOf(Short.MAX_VALUE); }
            case INTEGER -> { low = BigInteger.valueOf(Integer.MIN_VALUE); high = BigInteger.valueOf(Integer.MAX_VALUE); }
            case LONG -> { low = BigInteger.valueOf(Long.MIN_VALUE); high = BigInteger.valueOf(Long.MAX_VALUE); }
            case BIG_INTEGER -> { low = null; high = null; }
            default -> { return null; }
        }
        if (low != null && (value.compareTo(low) < 0 || value.compareTo(high) > 0)) return null;
        return new SolvikIr.Literal(LiteralKind.INT, value.toString(), type);
    }

    private SolvikIr booleanLiteral(boolean value, TypeModel.Type type) {
        return new SolvikIr.Literal(LiteralKind.BOOLEAN, Boolean.toString(value), type);
    }
}
