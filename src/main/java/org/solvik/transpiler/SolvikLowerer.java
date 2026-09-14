package org.solvik.transpiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.solvik.transpiler.Ast.*;
import static org.solvik.transpiler.TypeModel.*;
import org.solvik.transpiler.language.Literals;

import static org.solvik.transpiler.language.Language.*;

/**
 * Front-end lowering phase: analyzed AST to backend-neutral typed Solvik IR.
 *
 * <p>This is the only phase that walks {@code Ast} nodes. It resolves every
 * construct against the {@link SemanticAnalyzer.Model} produced by semantic
 * analysis and records the result in {@link SolvikProgram}, {@link SolvikStmt},
 * and {@link SolvikIr}. After this phase completes, no later phase may inspect
 * the parser AST: the optimizer, the Java backend, and any future backend see
 * only the typed Solvik IR.</p>
 *
 * <p>It is deliberately a plain, single-use object: it holds the current owner
 * and enclosing method only to resolve names, and drops them when lowering
 * finishes.</p>
 */
public final class SolvikLowerer {
    private final SemanticAnalyzer.Model model;
    private String owner;
    private MethodDecl currentMethod;
    private final Map<String, String> localRenames = new HashMap<>();
    private final Map<String, List<MethodDecl>> traitMethodCache = new HashMap<>();

    public SolvikLowerer(SemanticAnalyzer.Model model) {
        this.model = model;
    }

    /** Lowers the whole compilation unit into a typed {@link SolvikProgram}. */
    public SolvikProgram lower() {
        return lowerProgram();
    }

    private SolvikProgram lowerProgram() {
        List<SolvikProgram.Trait> traits = new ArrayList<>();
        List<SolvikProgram.Enum> enums = new ArrayList<>();
        List<SolvikProgram.Struct> structs = new ArrayList<>();
        for (Decl declaration : model.unit.declarations()) {
            if (declaration instanceof TraitDecl i) traits.add(lowerTrait(i));
            else if (declaration instanceof EnumDecl e) enums.add(lowerEnum(e));
        }
        int structIndex = 0;
        for (Decl declaration : model.unit.declarations()) if (declaration instanceof StructDecl s) structs.add(lowerStruct(s, structIndex++));
        return new SolvikProgram(traits, enums, structs);
    }

    private List<SolvikProgram.TypeParameter> lowerTypeParameters(List<TypeParam> params) {
        if (params.isEmpty()) return List.of();
        List<SolvikProgram.TypeParameter> result = new ArrayList<>(params.size());
        for (TypeParam p : params) result.add(new SolvikProgram.TypeParameter(p.name(), lowerTypeRefs(p.constraints())));
        return result;
    }

    private List<Type> lowerTypeRefs(List<TypeRef> refs) {
        if (refs.isEmpty()) return List.of();
        List<Type> result = new ArrayList<>(refs.size());
        for (TypeRef ref : refs) result.add(typeOf(ref));
        return result;
    }

    private SolvikProgram.Trait lowerTrait(TraitDecl declaration) {
        owner = declaration.name();
        List<SolvikProgram.Method> methods = new ArrayList<>();
        for (MethodDecl method : declaration.methods()) methods.add(lowerMethod(method));
        return new SolvikProgram.Trait(declaration.name(), lowerTypeParameters(declaration.typeParams()), lowerTypeRefs(declaration.extendsTypes()), methods);
    }

    private SolvikProgram.Enum lowerEnum(EnumDecl declaration) {
        owner = declaration.name();
        List<SolvikProgram.Variant> variants = new ArrayList<>();
        for (VariantDecl v : declaration.variants()) variants.add(new SolvikProgram.Variant(v.name(), v.payload() == null ? null : typeOf(v.payload())));
        return new SolvikProgram.Enum(declaration.name(), lowerTypeParameters(declaration.typeParams()), variants);
    }

    private SolvikProgram.Struct lowerStruct(StructDecl declaration, int index) {
        owner = declaration.name();
        List<SolvikProgram.Field> fields = new ArrayList<>();
        for (FieldDecl field : declaration.fields()) { Type type = typeOf(field.type()); fields.add(new SolvikProgram.Field(field.name(), type, field.isVar(), field.isStatic(), field.initializer() == null ? null : lower(field.initializer(), type))); }
        List<SolvikProgram.Method> methods = new ArrayList<>();
        for (MethodDecl method : declaration.methods()) methods.add(lowerMethod(method));
        methods.addAll(lowerDelegates(declaration));
        List<SolvikStmt> staticBlock = declaration.staticBlock() == null ? null : lowerBlock(declaration.staticBlock());
        return new SolvikProgram.Struct(declaration.name(), lowerTypeParameters(declaration.typeParams()), fields, methods, staticBlock, lowerTypeRefs(declaration.implementsTypes()), index);
    }

    private SolvikProgram.Method lowerMethod(MethodDecl method) {
        currentMethod = method;
        List<SolvikProgram.Parameter> params = new ArrayList<>();
        for (Param p : method.params()) params.add(new SolvikProgram.Parameter(p.name(), typeOf(p.type()), p.variadic(), methodParameterNeedsReferenceSignature(method, p)));
        List<SolvikStmt> body = null;
        if (method.body() != null) { body = lowerAccumulatorRecursion(method); if (body == null) body = lowerBlock(method.body()); }
        return new SolvikProgram.Method(method.name(), params, typeOf(method.returnType()), methodNeedsReferenceSignature(method), method.isPub(), method.instance(), lowerTypeParameters(methodTypeParametersList(method)), body);
    }

    private List<TypeParam> methodTypeParametersList(MethodDecl method) {
        List<TypeParam> parameters = new ArrayList<>();
        if (!method.instance() && owner != null && model.structs.containsKey(owner)) parameters.addAll(model.structs.get(owner).declaration.typeParams());
        for (TypeParam p : method.typeParams()) if (parameters.stream().noneMatch(x -> x.name().equals(p.name()))) parameters.add(p);
        return parameters;
    }

    private List<SolvikStmt> lowerAccumulatorRecursion(MethodDecl method) {
        if (method.instance() || method.params().size() != 1 || method.typeParams().size() != 0 || method.body().statements().size() != 2) return null;
        if (!(method.body().statements().get(0) instanceof IfStmt branch) || !(method.body().statements().get(1) instanceof ReturnStmt result)) return null;
        if (!(branch.condition() instanceof BinaryExpr condition) || condition.op() != BinaryOp.EQ || !(condition.left() instanceof NameExpr variable) || !(condition.right() instanceof Literal zero) || zero.kind() != LiteralKind.INT || !Literals.cleanInteger(zero.text()).equals("0")) return null;
        if (branch.thenBlock().statements().size() != 1 || !(branch.thenBlock().statements().get(0) instanceof ReturnStmt base) || !(base.value() instanceof Literal baseValue)) return null;
        if (!(result.value() instanceof BinaryExpr sum) || sum.op() != BinaryOp.ADD) return null;
        Expr term = null; CallExpr recursive = null;
        if (sum.left() instanceof CallExpr c) { recursive = c; term = sum.right(); } else if (sum.right() instanceof CallExpr c) { recursive = c; term = sum.left(); }
        if (recursive == null || !(recursive.callee() instanceof StaticExpr target) || !target.name().equals(method.name()) || recursive.arguments().size() != 1) return null;
        if (!(recursive.arguments().get(0).expression() instanceof BinaryExpr decrement) || decrement.op() != BinaryOp.SUB || !(decrement.left() instanceof NameExpr n) || !n.name().equals(variable.name()) || !(decrement.right() instanceof Literal one) || one.kind() != LiteralKind.INT || !Literals.cleanInteger(one.text()).equals("1")) return null;
        if (!method.returnType().name().equals("Long") || method.returnType().nullable() || !method.params().get(0).type().name().equals("Long") || method.params().get(0).type().nullable()) return null;
        Type longType = named(Base.LONG, "Long"); Type boolType = named(Base.BOOLEAN, "Boolean");
        localRenames.put(variable.name(), "__value");
        SolvikIr termIr = lower(term, longType);
        localRenames.remove(variable.name());
        List<SolvikStmt> body = new ArrayList<>();
        body.add(new SolvikStmt.VarDecl("__acc", longType, new SolvikIr.Literal(LiteralKind.INT, Literals.cleanInteger(baseValue.text()), longType)));
        body.add(new SolvikStmt.VarDecl("__value", longType, new SolvikIr.Local(variable.name(), longType)));
        SolvikIr loopCondition = new SolvikIr.Unary(UnaryOp.NOT, new SolvikIr.Binary(BinaryOp.EQ, new SolvikIr.Local("__value", longType), new SolvikIr.Literal(LiteralKind.INT, "0", longType), longType, longType, boolType), boolType, boolType);
        List<SolvikStmt> loopBody = new ArrayList<>();
        loopBody.add(new SolvikStmt.Expr(new SolvikIr.Update(BinaryOp.ADD, new SolvikIr.Local("__acc", longType), termIr, longType, longType, longType)));
        loopBody.add(new SolvikStmt.Expr(new SolvikIr.Update(BinaryOp.SUB, new SolvikIr.Local("__value", longType), new SolvikIr.Literal(LiteralKind.INT, "1", longType), longType, longType, longType)));
        body.add(new SolvikStmt.While(loopCondition, loopBody));
        body.add(new SolvikStmt.Return(new SolvikIr.Local("__acc", longType)));
        return body;
    }

    private List<SolvikProgram.Method> lowerDelegates(StructDecl declaration) {
        List<SolvikProgram.Method> result = new ArrayList<>(); Set<String> emitted = new java.util.HashSet<>();
        for (DelegateDecl delegate : declaration.delegates()) {
            TraitDecl iface = model.traits.get(delegate.traitType().name()).declaration;
            Type ifaceType = typeOf(delegate.traitType());
            for (MethodDecl method : traitMethods(iface)) if (emitted.add(method.name()) && declaration.methods().stream().noneMatch(x -> x.name().equals(method.name()))) {
                currentMethod = method;
                List<SolvikProgram.Parameter> params = new ArrayList<>();
                List<SolvikIr> args = new ArrayList<>();
                for (Param p : method.params()) { Type parameter = delegateTypeOf(p.type(), ifaceType); params.add(new SolvikProgram.Parameter(p.name(), parameter, p.variadic(), traitTypeVariable(p.type(), iface))); args.add(new SolvikIr.Local(p.name(), parameter)); }
                Type returnType = delegateTypeOf(method.returnType(), ifaceType);
                SolvikIr.Call call = new SolvikIr.Call(new SolvikIr.Field(owner, delegate.field(), false, ifaceType), null, false, null, false, method.name(), null, args, ifaceType, returnType);
                List<SolvikStmt> body = new ArrayList<>();
                if (returnType.base() == Base.VOID) body.add(new SolvikStmt.Expr(call)); else body.add(new SolvikStmt.Return(call));
                result.add(new SolvikProgram.Method(method.name(), params, returnType, traitTypeVariable(method.returnType(), iface), true, true, lowerTypeParameters(methodTypeParametersList(method)), body));
            }
        }
        return result;
    }

    private List<MethodDecl> traitMethods(TraitDecl iface) {
        List<MethodDecl> cached = traitMethodCache.get(iface.name());
        if (cached != null) return cached;
        List<MethodDecl> result = new ArrayList<>(iface.methods());
        for (TypeRef parent : iface.extendsTypes()) {
            TraitDecl inherited = model.traits.containsKey(parent.name()) ? model.traits.get(parent.name()).declaration : null;
            if (inherited != null) for (MethodDecl method : traitMethods(inherited)) if (result.stream().noneMatch(x -> x.name().equals(method.name()))) result.add(method);
        }
        List<MethodDecl> immutable = List.copyOf(result);
        traitMethodCache.put(iface.name(), immutable);
        return immutable;
    }

    private boolean methodNeedsReferenceSignature(MethodDecl method) {
        if (owner == null || !model.structs.containsKey(owner) || !method.instance()) return false;
        StructDecl declaration = model.structs.get(owner).declaration;
        for (TypeRef implemented : declaration.implementsTypes()) {
            Type ifaceType = typeOf(implemented);
            if (!model.traits.containsKey(ifaceType.name())) continue;
            TraitDecl iface = model.traits.get(ifaceType.name()).declaration;
            for (MethodDecl candidate : traitMethods(iface)) {
                if (candidate.name().equals(method.name()) && candidate.params().size() == method.params().size()) {
                    if (traitTypeVariable(candidate.returnType(), iface)) return true;
                    for (int i = 0; i < candidate.params().size(); i++) if (traitTypeVariable(candidate.params().get(i).type(), iface)) return true;
                }
            }
        }
        return false;
    }

    private boolean methodParameterNeedsReferenceSignature(MethodDecl method, Param parameter) {
        if (owner == null || !model.structs.containsKey(owner) || !method.instance()) return false;
        StructDecl declaration = model.structs.get(owner).declaration;
        for (TypeRef implemented : declaration.implementsTypes()) {
            Type ifaceType = typeOf(implemented);
            if (!model.traits.containsKey(ifaceType.name())) continue;
            TraitDecl iface = model.traits.get(ifaceType.name()).declaration;
            for (MethodDecl candidate : traitMethods(iface)) {
                if (!candidate.name().equals(method.name()) || candidate.params().size() != method.params().size()) continue;
                for (int i = 0; i < candidate.params().size(); i++) if (candidate.params().get(i).name().equals(parameter.name())) return traitTypeVariable(candidate.params().get(i).type(), iface);
            }
        }
        return false;
    }

    private boolean traitTypeVariable(TypeRef ref, TraitDecl iface) {
        if (ref == null) return false;
        if (iface.typeParams().stream().anyMatch(p -> p.name().equals(ref.name()))) return true;
        return ref.args().stream().anyMatch(arg -> traitTypeVariable(arg, iface));
    }

    private Type delegateTypeOf(TypeRef ref, Type ifaceType) {
        if (ref == null) return named(Base.VOID, "Void");
        List<TypeParam> params = model.traits.containsKey(ifaceType.name()) ? model.traits.get(ifaceType.name()).declaration.typeParams() : List.of();
        for (int i = 0; i < params.size() && i < ifaceType.args().size(); i++) if (params.get(i).name().equals(ref.name())) return ifaceType.args().get(i);
        if (currentMethod != null && currentMethod.typeParams().stream().anyMatch(p -> p.name().equals(ref.name()))) return var(ref.name());
        if (ref.name().equals("Self")) return new Type(Base.STRUCT, owner, model.structs.get(owner).declaration.typeParams().stream().map(p -> var(p.name())).toList(), ref.nullable());
        List<Type> args = ref.args().stream().map(x -> delegateTypeOf(x, ifaceType)).toList();
        Type t = fromName(ref.name(), args, ref.nullable());
        if (t.base() == Base.UNKNOWN) { if (model.structs.containsKey(ref.name())) t = new Type(Base.STRUCT, ref.name(), args, ref.nullable()); else if (model.traits.containsKey(ref.name())) t = new Type(Base.TRAIT, ref.name(), args, ref.nullable()); else if (model.enums.containsKey(ref.name())) t = new Type(Base.ENUM, ref.name(), args, ref.nullable()); }
        return t;
    }

    private List<SolvikStmt> lowerBlock(Block block) { List<SolvikStmt> result = new ArrayList<>(); for (Stmt statement : block.statements()) result.add(lowerStmt(statement)); return result; }

    private SolvikStmt lowerStmt(Stmt statement) {
        if (statement instanceof VarDecl d) { Type declared = typeOf(d.type()); return new SolvikStmt.VarDecl(d.name(), declared, d.initializer() == null ? null : lowerValue(d.initializer(), declared)); }
        if (statement instanceof ExprStmt e) { if (e.expression() instanceof MatchExpr m) { Type subjectType = model.type(m.subject()); List<SolvikIr.MatchArm> arms = new ArrayList<>(); for (MatchArm arm : m.arms()) arms.add(new SolvikIr.MatchArm(lowerPattern(arm.pattern(), subjectType), lower(arm.body(), null))); return new SolvikStmt.MatchStmt(lower(m.subject(), null), arms); } return new SolvikStmt.Expr(lower(e.expression(), null)); }
        if (statement instanceof ReturnStmt r) { Type declared = typeOf(currentMethod.returnType()); return new SolvikStmt.Return(r.value() == null ? null : lowerValue(r.value(), declared)); }
        if (statement instanceof IfStmt i) { List<SolvikStmt> thenBranch = lowerBlock(i.thenBlock()); List<SolvikStmt> elseBranch = new ArrayList<>(); if (i.elseBranch() != null) { if (i.elseBranch() instanceof BlockStmt b) elseBranch = lowerBlock(b.block()); else elseBranch.add(lowerStmt(i.elseBranch())); } return new SolvikStmt.If(lower(i.condition(), named(Base.BOOLEAN, "Boolean")), thenBranch, elseBranch); }
        if (statement instanceof WhileStmt w) return new SolvikStmt.While(lower(w.condition(), named(Base.BOOLEAN, "Boolean")), lowerBlock(w.body()));
        if (statement instanceof ForStmt f) {
            Type iterable = model.type(f.iterable());
            if (f.iterable() instanceof BinaryExpr b && b.op() == BinaryOp.CONCAT && model.type(b.left()).integral() && model.type(b.right()).integral()) {
                Type loopType = binaryPromotion(model.type(b.left()), model.type(b.right()));
                return new SolvikStmt.ForRange(f.name(), loopType, lower(b.left(), loopType), lower(b.right(), loopType), false, lowerBlock(f.body()));
            }
            return new SolvikStmt.ForEach(f.name(), iterableElement(iterable), lower(f.iterable(), iterable), lowerBlock(f.body()));
        }
        if (statement instanceof SwitchStmt s) { Type subjectType = model.type(s.subject()); List<SolvikStmt.SwitchCase> cases = new ArrayList<>(); for (SwitchCase c : s.cases()) { List<SolvikIr> values = new ArrayList<>(); for (Expr v : c.values()) values.add(lower(v, subjectType)); cases.add(new SolvikStmt.SwitchCase(c.isDefault(), values, lowerBlock(c.body()))); } return new SolvikStmt.Switch(lower(s.subject(), subjectType), subjectType, directEqualityType(subjectType), cases); }
        if (statement instanceof TryStmt t) { List<SolvikStmt.Catch> catches = new ArrayList<>(); for (CatchClause c : t.catches()) catches.add(new SolvikStmt.Catch(typeOf(c.type()), c.name(), lowerBlock(c.body()))); return new SolvikStmt.Try(lowerBlock(t.body()), catches, t.finallyBlock() == null ? null : lowerBlock(t.finallyBlock())); }
        if (statement instanceof ThrowStmt t) return new SolvikStmt.Throw(lower(t.value(), null));
        if (statement instanceof BreakStmt) return new SolvikStmt.Break();
        if (statement instanceof ContinueStmt) return new SolvikStmt.Continue();
        if (statement instanceof BlockStmt b) return new SolvikStmt.Block(lowerBlock(b.block()));
        if (statement instanceof AtomicStmt a) {
            List<SolvikIr> targets = new ArrayList<>(a.targets().size());
            for (Expr target : a.targets()) targets.add(lower(target, null));
            return new SolvikStmt.Atomic(targets, lowerBlock(a.block()));
        }
        throw new InternalCompilerException("unlowered statement: " + statement);
    }

    private Type iterableElement(Type t) { return switch (t.base()) { case STRING -> named(Base.CHAR, "Char"); case RANGE -> named(Base.LONG, "Long"); default -> t.args().isEmpty() ? named(Base.OBJECT, "Object") : t.args().get(0); }; }

    private boolean directEqualityType(Type type) {
        return type != null && !type.nullable() && (type.base() == Base.BOOLEAN || type.base() == Base.STRING
                || type.base() == Base.CHAR || type.numeric());
    }

    private SolvikIr lowerValue(Expr expression, Type expected) {
        SolvikIr value = lower(expression, expected);
        Type actual = model.type(expression);
        if (expected != null && actual != null && !(expression instanceof Literal) && (expected.base() == Base.BYTE || expected.base() == Base.SHORT) && actual.integral() && actual.base() != expected.base() && !expected.nullable() && model.constantInteger(expression) != null) {
            return new SolvikIr.Coerce(value, actual, expected, true);
        }
        if (expected == null || actual == null || expression instanceof Literal || !actual.primitive() || !expected.primitive() || actual.base() == expected.base()) return value;
        if (!numericWidening(actual.base(), expected.base())) return value;
        return new SolvikIr.Coerce(value, actual, expected, false);
    }

    private SolvikIr lower(Expr expression, Type expected) {
        if (expression instanceof NameExpr n) {
            if (n.name().equals("self")) return new SolvikIr.Self(model.type(n));
            if (model.fieldNames.containsKey(n)) { SemanticAnalyzer.FieldInfo info = model.fieldNames.get(n); return new SolvikIr.Field(owner, n.name(), info.declaration.isStatic(), model.type(n)); }
            if (model.structs.containsKey(owner) && model.structs.get(owner).fields.containsKey(n.name()) && model.structs.get(owner).fields.get(n.name()).declaration.isStatic()) return new SolvikIr.Field(owner, n.name(), true, model.type(n));
            return new SolvikIr.Local(localRenames.getOrDefault(n.name(), n.name()), model.type(n));
        }
        if (expression instanceof UnaryExpr u) return lowerUnary(u);
        if (expression instanceof BinaryExpr b) return lowerBinary(b);
        if (expression instanceof CoalesceExpr c) return new SolvikIr.Coalesce(lower(c.left(), null), lower(c.right(), model.type(c.left()).nonNull()), model.type(c));
        if (expression instanceof RangeExpr r) return new SolvikIr.Range(lower(r.start(), named(Base.LONG, "Long")), lower(r.end(), named(Base.LONG, "Long")), r.inclusive(), model.type(r));
        if (expression instanceof Literal l) return new SolvikIr.Literal(l.kind(), l.text(), expected != null ? expected : model.type(l));
        if (expression instanceof ListExpr l) { Type inferred = model.type(l); Type element = expected != null && expected.base() == Base.LIST && !expected.args().isEmpty() ? expected.args().get(0) : inferred.args().isEmpty() ? named(Base.OBJECT, "Object") : inferred.args().get(0); return new SolvikIr.ListLiteral(l.elements().stream().map(x -> lower(x, element)).toList(), element, inferred); }
        if (expression instanceof MapExpr m) { Type mapType = expected != null && expected.base() == Base.MAP ? expected : model.type(m); Type key = mapType.args().size()>0?mapType.args().get(0):named(Base.OBJECT,"Object"); Type value = mapType.args().size()>1?mapType.args().get(1):named(Base.OBJECT,"Object"); return new SolvikIr.MapLiteral(m.entries().stream().map(x -> java.util.Map.entry(lower(x.key(), key), lower(x.value(), value))).toList(), key, value, model.type(m)); }
        if (expression instanceof MemberExpr m) return new SolvikIr.Member(lower(m.object(), null), m.name(), model.memberFields.containsKey(m), model.type(m));
        if (expression instanceof StaticExpr s) return new SolvikIr.StaticRef(s.name(), typeOf(s.type()));
        if (expression instanceof AssignExpr a) return new SolvikIr.Assign(lower(a.target(), null), lower(a.value(), model.type(a.target())), model.type(a));
        if (expression instanceof CallExpr c) return lowerCall(c);
        if (expression instanceof UpdateExpr u) { Type t = model.type(u); Type valueType = model.type(u.value()); if (u.op() == BinaryOp.CONCAT) return new SolvikIr.Update(u.op(), lower(u.target(), null), lower(u.value(), named(Base.STRING, "String")), t, t, valueType); Type promoted = binaryPromotion(t, valueType); if (promoted == null) promoted = t; return new SolvikIr.Update(u.op(), lower(u.target(), null), lower(u.value(), promoted), promoted, t, valueType); }
        if (expression instanceof SelfInitExpr s) { StructDecl declaration = model.structs.get(owner).declaration; java.util.Map<String, Expr> values = new HashMap<>(); for (FieldInit f : s.fields()) values.put(f.name(), f.value()); List<SolvikIr> args = new ArrayList<>(); for (FieldDecl f : declaration.fields()) if (!f.isStatic()) args.add(values.containsKey(f.name()) ? lower(values.get(f.name()), typeOf(f.type())) : new SolvikIr.DefaultValue(typeOf(f.type()))); return new SolvikIr.SelfInit(owner, args, model.type(s)); }
        if (expression instanceof MatchExpr m) { Type subjectType = model.type(m.subject()); List<SolvikIr.MatchArm> arms = new ArrayList<>(); for (MatchArm arm : m.arms()) arms.add(new SolvikIr.MatchArm(lowerPattern(arm.pattern(), subjectType), lower(arm.body(), model.type(m)))); return new SolvikIr.Match(lower(m.subject(), null), arms, model.type(m)); }
        return new SolvikIr.Literal(LiteralKind.NULL, "null", named(Base.OBJECT, "Object"));
    }

    private SolvikIr lowerUnary(UnaryExpr u) {
        Type operandType = model.type(u.operand());
        return new SolvikIr.Unary(u.op(), lower(u.operand(), operandType), operandType, model.type(u));
    }

    private SolvikIr lowerBinary(BinaryExpr b) {
        Type result = model.type(b), leftType = model.type(b.left()), rightType = model.type(b.right());
        return new SolvikIr.Binary(b.op(), lower(b.left(), result), lower(b.right(), result), leftType, rightType, result);
    }

    private SolvikIr lowerCall(CallExpr call) {
        SemanticAnalyzer.CallInfo info = model.calls.get(call);
        if (info == null) return new SolvikIr.Literal(LiteralKind.NULL, "null", named(Base.OBJECT, "Object"));
        SolvikIr receiver = null; Type staticOwnerType = null; boolean staticTypeArguments = false; String implicitOwner = null; boolean implicitInstance = false; String name;
        if (call.callee() instanceof StaticExpr s) { staticOwnerType = typeOf(s.type()); staticTypeArguments = info.method() != null && !info.method().declaration.instance() && info.receiverType() != null && !info.receiverType().args().isEmpty(); name = s.name(); }
        else if (call.callee() instanceof MemberExpr m) { receiver = lower(m.object(), null); name = m.name(); }
        else { name = ((NameExpr) call.callee()).name(); implicitOwner = owner; implicitInstance = currentMethod != null && currentMethod.instance(); }
        List<SolvikIr> arguments = info.builtin() != null ? lowerBuiltinArguments(call, info) : lowerCallArguments(call, info);
        return new SolvikIr.Call(receiver, staticOwnerType, staticTypeArguments, implicitOwner, implicitInstance, name, info.builtin(), arguments, info.receiverType(), model.type(call));
    }

    private List<SolvikIr> lowerCallArguments(CallExpr call, SemanticAnalyzer.CallInfo info) {
        List<Expr> ordered = info.orderedArguments(); List<SolvikIr> result = new ArrayList<>();
        if (info.method() != null && info.variadic()) {
            MethodDecl method = info.method().declaration; int fixed = method.params().size() - 1;
            for (int i = 0; i < fixed && i < ordered.size(); i++) result.add(lowerValue(ordered.get(i), callParameterType(info, method.params().get(i).type())));
            Type element = callParameterType(info, method.params().get(method.params().size()-1).type());
            if (ordered.size() == fixed + 1 && call.arguments().stream().anyMatch(a -> a.spread() && a.expression() == ordered.get(fixed))) result.add(lowerValue(ordered.get(fixed), genericListType(element)));
            else { List<SolvikIr> rest = new ArrayList<>(); for (int i = fixed; i < ordered.size(); i++) rest.add(lowerValue(ordered.get(i), element)); result.add(new SolvikIr.ListLiteral(rest, element, genericListType(element))); }
        } else if (info.method() != null) {
            List<Param> params = info.method().declaration.params();
            for (int i = 0; i < ordered.size(); i++) result.add(lowerValue(ordered.get(i), i < params.size() ? callParameterType(info, params.get(i).type()) : null));
        } else {
            for (Expr argument : ordered) result.add(lowerValue(argument, null));
        }
        return result;
    }

    private List<SolvikIr> lowerBuiltinArguments(CallExpr call, SemanticAnalyzer.CallInfo info) {
        String builtin = info.builtin(); List<SolvikIr> result = new ArrayList<>();
        if (builtin.startsWith("enum:")) {
            String variant = builtin.substring(5); Type payload = null;
            if (info.receiverType() != null && model.enums.containsKey(info.receiverType().name())) for (VariantDecl candidate : model.enums.get(info.receiverType().name()).declaration.variants()) if (candidate.name().equals(variant) && candidate.payload() != null) payload = enumPayloadType(candidate.payload(), info.receiverType());
            for (Arg argument : call.arguments()) result.add(lowerValue(argument.expression(), payload));
            return result;
        }
        if (builtin.startsWith("object:") || builtin.startsWith("dynamic:")) { for (Arg argument : call.arguments()) result.add(lowerValue(argument.expression(), null)); return result; }
        int dot = builtin.indexOf('.'); String namespace = dot < 0 ? builtin : builtin.substring(0, dot); String method = dot < 0 ? builtin : builtin.substring(dot + 1);
        if (call.callee() instanceof MemberExpr) { for (int i = 0; i < call.arguments().size(); i++) result.add(lowerValue(call.arguments().get(i).expression(), builtinArgumentType(info.receiverType(), method, i))); return result; }
        if (namespace.equals("Math") && method.equals("abs") && !call.arguments().isEmpty()) { Type argument = model.type(call.arguments().get(0).expression()); if (argument.base() == Base.LONG) { result.add(lowerValue(call.arguments().get(0).expression(), argument)); return result; } if (argument.integral()) { result.add(lowerValue(call.arguments().get(0).expression(), named(Base.INTEGER, "Integer"))); return result; } }
        for (Arg argument : call.arguments()) result.add(lowerValue(argument.expression(), null));
        return result;
    }

    private Type builtinArgumentType(Type receiver, String method, int index) {
        if (receiver == null) return null;
        if (receiver.base() == Base.LIST) return (method.equals("addAt") && index == 0) || (method.equals("set") && index == 0) || method.equals("get") || method.equals("remove") ? named(Base.INTEGER,"Integer") : receiver.args().isEmpty()?null:receiver.args().get(0);
        if (receiver.base() == Base.MAP) { boolean valueFirst = method.equals("containsValue"); if (index == 0) return valueFirst ? (receiver.args().size()>1?receiver.args().get(1):null) : (receiver.args().isEmpty()?null:receiver.args().get(0)); return receiver.args().size()>1?receiver.args().get(1):null; }
        if (receiver.base() == Base.SET || receiver.base() == Base.STACK) return receiver.args().isEmpty()?null:receiver.args().get(0);
        if (receiver.base() == Base.STRING && (method.equals("substring") || method.equals("charAt"))) return named(Base.LONG,"Long");
        return null;
    }

    private Type callParameterType(SemanticAnalyzer.CallInfo info, TypeRef ref) {
        if (ref == null) return named(Base.VOID, "Void");
        if (info.receiverType() != null && info.method() != null) {
            List<TypeParam> ownerParams = model.structs.containsKey(info.method().owner)
                    ? model.structs.get(info.method().owner).declaration.typeParams()
                    : model.traits.containsKey(info.method().owner) ? model.traits.get(info.method().owner).declaration.typeParams() : List.of();
            for (int i = 0; i < ownerParams.size() && i < info.receiverType().args().size(); i++) if (ownerParams.get(i).name().equals(ref.name())) return info.receiverType().args().get(i);
        }
        if (ref.name().equals("Self") && info.receiverType() != null) return info.receiverType();
        List<Type> args = ref.args().stream().map(x -> callParameterType(info, x)).toList();
        Type t = fromName(ref.name(), args, ref.nullable());
        if (t.base() == Base.UNKNOWN) {
            if (model.structs.containsKey(ref.name())) t = new Type(Base.STRUCT, ref.name(), args, ref.nullable());
            else if (model.traits.containsKey(ref.name())) t = new Type(Base.TRAIT, ref.name(), args, ref.nullable());
            else if (model.enums.containsKey(ref.name())) t = new Type(Base.ENUM, ref.name(), args, ref.nullable());
        }
        return t;
    }

    private Type genericListType(Type element) { return generic(Base.LIST, "List", List.of(element)); }

    private Type enumPayloadType(TypeRef ref, Type enumType) {
        List<TypeParam> parameters = model.enums.containsKey(enumType.name()) ? model.enums.get(enumType.name()).declaration.typeParams() : List.of();
        for (int i = 0; i < parameters.size() && i < enumType.args().size(); i++) if (parameters.get(i).name().equals(ref.name())) return enumType.args().get(i);
        List<Type> args = ref.args().stream().map(x -> enumPayloadType(x, enumType)).toList();
        Type t = fromName(ref.name(), args, ref.nullable());
        if (t.base() == Base.UNKNOWN) {
            if (model.structs.containsKey(ref.name())) t = new Type(Base.STRUCT, ref.name(), args, ref.nullable());
            else if (model.traits.containsKey(ref.name())) t = new Type(Base.TRAIT, ref.name(), args, ref.nullable());
            else if (model.enums.containsKey(ref.name())) t = new Type(Base.ENUM, ref.name(), args, ref.nullable());
        }
        return t;
    }

    private SolvikIr.SolvikPattern lowerPattern(Pattern pattern, Type subjectType) {
        if (pattern instanceof WildcardPattern) return new SolvikIr.SolvikPattern.Wildcard();
        if (pattern instanceof BindPattern b) return new SolvikIr.SolvikPattern.Bind(b.name(), subjectType);
        if (pattern instanceof LiteralPattern p) return new SolvikIr.SolvikPattern.Literal(new SolvikIr.Literal(p.literal().kind(), p.literal().text(), subjectType));
        if (pattern instanceof ListPattern p) { Type element = subjectType.args().isEmpty() ? named(Base.OBJECT, "Object") : subjectType.args().get(0); List<SolvikIr.SolvikPattern> elements = new ArrayList<>(); for (Pattern x : p.elements()) elements.add(lowerPattern(x, element)); return new SolvikIr.SolvikPattern.ListPattern(elements, element); }
        if (pattern instanceof VariantPattern p) { String enumName = subjectType.name(); EnumDecl e = model.enums.containsKey(enumName) ? model.enums.get(enumName).declaration : null; int tag = 0; if (e != null) for (int i = 0; i < e.variants().size(); i++) if (e.variants().get(i).name().equals(p.name())) tag = i; Type payloadType = named(Base.OBJECT, "Object"); if (e != null) for (VariantDecl v : e.variants()) if (v.name().equals(p.name()) && v.payload() != null) payloadType = enumPayloadType(v.payload(), subjectType); List<SolvikIr.SolvikPattern> payload = new ArrayList<>(); for (Pattern x : p.payload()) payload.add(lowerPattern(x, payloadType)); return new SolvikIr.SolvikPattern.Variant(enumName, tag, payloadType, payload); }
        return new SolvikIr.SolvikPattern.Wildcard();
    }

    private Type typeOf(TypeRef ref) {
        if (ref == null) return named(Base.VOID, "Void");
        String n = ref.name().equals("Self") ? owner : ref.name();
        if (ref.name().equals("Self") && owner != null && model.structs.containsKey(owner)) {
            return new Type(Base.STRUCT, owner, typeParamNames(model.structs.get(owner).declaration.typeParams()), ref.nullable());
        }
        // A name is a type parameter when it is bound by the owner struct, the
        // owner trait, the owner enum, or the current method. Each test is a
        // plain loop over a small list: using streams here would allocate one
        // stream per test for every type reference the emitter lowers, and the
        // owner/method type-parameter lists are tiny, so a linear scan wins.
        if (owner != null && model.structs.containsKey(owner)
                && hasTypeParam(model.structs.get(owner).declaration.typeParams(), n)) return var(n);
        if (owner != null && model.traits.containsKey(owner)
                && hasTypeParam(model.traits.get(owner).declaration.typeParams(), n)) return var(n);
        if (owner != null && model.enums.containsKey(owner)
                && hasTypeParam(model.enums.get(owner).declaration.typeParams(), n)) return var(n);
        if (currentMethod != null && hasTypeParam(currentMethod.typeParams(), n)) return var(n);
        List<Type> args = lowerTypeRefs(ref.args()); Type t = fromName(n,args,ref.nullable());
        if (t.base() == Base.UNKNOWN) { if(model.structs.containsKey(n)) t=new Type(Base.STRUCT,n,args,ref.nullable()); else if(model.traits.containsKey(n)) t=new Type(Base.TRAIT,n,args,ref.nullable()); else if(model.enums.containsKey(n)) t=new Type(Base.ENUM,n,args,ref.nullable()); }
        return t;
    }

    private boolean hasTypeParam(List<TypeParam> params, String name) {
        for (TypeParam p : params) if (p.name().equals(name)) return true;
        return false;
    }

    private List<Type> typeParamNames(List<TypeParam> params) {
        List<Type> args = new ArrayList<>(params.size());
        for (TypeParam p : params) args.add(var(p.name()));
        return args;
    }
}
