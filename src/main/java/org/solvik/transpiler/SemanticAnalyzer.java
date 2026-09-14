package org.solvik.transpiler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.solvik.transpiler.Ast.*;
import static org.solvik.transpiler.TypeModel.*;

/** Name resolver and semantic checker. javac is only a later output check. */
public final class SemanticAnalyzer {
    public static final class FieldInfo {
        public final FieldDecl declaration; public final Type type;
        FieldInfo(FieldDecl declaration, Type type) { this.declaration = declaration; this.type = type; }
    }
    public static final class MethodInfo {
        public final String owner; public final MethodDecl declaration; public final List<Type> typeParameters;
        MethodInfo(String owner, MethodDecl declaration, List<Type> typeParameters) { this.owner = owner; this.declaration = declaration; this.typeParameters = typeParameters; }
    }
    public static final class StructInfo {
        public final StructDecl declaration; public final Map<String, FieldInfo> fields = new HashMap<>(); public final Map<String, MethodInfo> methods = new HashMap<>();
        StructInfo(StructDecl declaration) { this.declaration = declaration; }
    }
    public static final class TraitInfo {
        public final TraitDecl declaration; public final Map<String, MethodInfo> methods = new HashMap<>();
        TraitInfo(TraitDecl declaration) { this.declaration = declaration; }
    }
    public static final class EnumInfo { public final EnumDecl declaration; EnumInfo(EnumDecl d) { declaration = d; } }

    public record CallInfo(Type result, MethodInfo method, List<Expr> orderedArguments, boolean variadic,
                           String builtin, Type receiverType) {}

    public static final class Model {
        public final CompilationUnit unit;
        public final Map<String, StructInfo> structs; public final Map<String, TraitInfo> traits; public final Map<String, EnumInfo> enums;
        public final IdentityHashMap<Expr, Type> expressionTypes = new IdentityHashMap<>();
        public final IdentityHashMap<CallExpr, CallInfo> calls = new IdentityHashMap<>();
        public final IdentityHashMap<NameExpr, FieldInfo> fieldNames = new IdentityHashMap<>();
        public final IdentityHashMap<MemberExpr, FieldInfo> memberFields = new IdentityHashMap<>();
        /** Memoized constant-integer values per expression node (null = not a constant). */
        public final IdentityHashMap<Expr, BigInteger> constantIntegers = new IdentityHashMap<>();

        /** Resolves the exact integer value of an expression once and caches it. */
        public BigInteger constantInteger(Expr e) {
            if (constantIntegers.containsKey(e)) return constantIntegers.get(e);
            BigInteger value = SemanticAnalyzer.constantInteger(e);
            constantIntegers.put(e, value);
            return value;
        }
        Model(CompilationUnit unit, Map<String, StructInfo> s, Map<String, TraitInfo> i, Map<String, EnumInfo> e) { this.unit = unit; structs = s; traits = i; enums = e; }
        public Type type(Expr e) { return expressionTypes.getOrDefault(e, named(Base.OBJECT, "Object")); }
    }

    private final List<Diagnostic> errors = new ArrayList<>();
    private final Map<String, StructInfo> structs = new HashMap<>();
    private final Map<String, TraitInfo> traits = new HashMap<>();
    private final Map<String, EnumInfo> enums = new HashMap<>();
    private Model model; private String owner; private boolean staticContext; private MethodDecl currentMethod;
    private final ArrayList<Map<String, Type>> scopes = new ArrayList<>();
    private final ArrayList<Map<String, Boolean>> mutableScopes = new ArrayList<>();
    private final ArrayList<Set<String>> assignedScopes = new ArrayList<>();
    private final ArrayList<Map<String, Type>> narrowingScopes = new ArrayList<>();
    /**
     * Undo log of definite-assignment additions as parallel (set, name) lists.
     * Snapshots are log sizes; restoring pops additions instead of copying every
     * assigned set, which keeps control-flow joins proportional to the number of
     * assignments in the joined branches rather than the total variable count.
     */
    private final ArrayList<Set<String>> assignLogSets = new ArrayList<>();
    private final ArrayList<String> assignLogNames = new ArrayList<>();
    private final Map<String, Type> typeVariables = new HashMap<>();
    private final Set<Span> reportedGenericArity = new HashSet<>();
    private final Set<Span> reportedNullableTypeArguments = new HashSet<>();
    private final Map<String, List<MethodDecl>> traitMethodCache = new HashMap<>();
    private final IdentityHashMap<StructInfo, Set<String>> delegatedMethodCache = new IdentityHashMap<>();
    private static final Set<String> BUILTIN_NAMESPACES = Set.of("System", "Math", "Base64", "Hash", "Json", "Time", "Random", "Type", "File", "Test");
    private static final Set<String> RANDOM_METHODS = Set.of("nextLong", "nextDouble", "seed");

    public Model analyze(CompilationUnit unit) throws CompileException {
        for (Decl d : unit.declarations()) {
            if (structs.containsKey(d.name()) || traits.containsKey(d.name()) || enums.containsKey(d.name())) fail("C100", "duplicate type '" + d.name() + "'", d.span());
            if (d instanceof StructDecl s) structs.put(s.name(), new StructInfo(s));
            else if (d instanceof TraitDecl i) traits.put(i.name(), new TraitInfo(i));
            else if (d instanceof EnumDecl e) enums.put(e.name(), new EnumInfo(e));
        }
        model = new Model(unit, structs, traits, enums);
        for (StructInfo s : structs.values()) indexStruct(s);
        for (TraitInfo i : traits.values()) indexTrait(i);
        for (String name : traits.keySet()) detectTraitCycle(name, new HashSet<>(), new HashSet<>());
        for (StructInfo s : structs.values()) checkStruct(s);
        for (TraitInfo i : traits.values()) checkTrait(i);
        checkEntryPoint();
        if (!errors.isEmpty()) throw new CompileException(errors);
        return model;
    }

    private void detectTraitCycle(String name, Set<String> active, Set<String> done) {
        if (done.contains(name)) return;
        if (!active.add(name)) { fail("C125", "trait inheritance cycle involving '" + name + "'", traits.get(name).declaration.span()); return; }
        TraitInfo info = traits.get(name);
        if (info != null) for (TypeRef parent : info.declaration.extendsTypes()) if (traits.containsKey(parent.name())) detectTraitCycle(parent.name(), active, done);
        active.remove(name); done.add(name);
    }

    private void indexStruct(StructInfo info) {
        typeVariables.clear(); for (TypeParam p : info.declaration.typeParams()) typeVariables.put(p.name(), var(p.name()));
        for (FieldDecl f : info.declaration.fields()) {
            if (info.fields.containsKey(f.name())) fail("C101", "duplicate field '" + f.name() + "'", f.span());
            info.fields.put(f.name(), new FieldInfo(f, resolve(f.type(), info.declaration.name())));
        }
        for (MethodDecl m : info.declaration.methods()) {
            if (info.methods.containsKey(m.name())) fail("C102", "duplicate method '" + m.name() + "'", m.span());
            info.methods.put(m.name(), new MethodInfo(info.declaration.name(), m, info.declaration.typeParams().stream().map(p -> var(p.name())).toList()));
        }
    }
    private void indexTrait(TraitInfo info) {
        for (MethodDecl m : info.declaration.methods()) info.methods.put(m.name(), new MethodInfo(info.declaration.name(), m, info.declaration.typeParams().stream().map(p -> var(p.name())).toList()));
    }

    private void checkStruct(StructInfo info) {
        owner = info.declaration.name();
        for (FieldDecl f : info.declaration.fields()) if (f.isStatic() && f.initializer() != null) { staticContext = true; currentMethod = null; push(); checkExpr(f.initializer(), info.fields.get(f.name()).type); pop(); }
        if (info.declaration.staticBlock() != null) { staticContext = true; currentMethod = null; push(); checkBlock(info.declaration.staticBlock()); pop(); }
        for (MethodDecl m : info.declaration.methods()) checkMethod(info, m);
        checkDelegates(info);
        // Every declared trait must be known and every required method must be present or defaulted/delegated.
        Map<String, MethodDecl> defaults = new HashMap<>();
        for (TypeRef ref : info.declaration.implementsTypes()) {
            Type t = resolve(ref, owner); if (t.base() == Base.RUNNABLE) continue; if (t.base() != Base.TRAIT) { fail("C123", "'" + ref.name() + "' is not a trait", ref.span()); continue; }
            TraitInfo iface = traits.get(t.name()); if (iface == null) continue;
            for (MethodDecl method : traitMethods(iface)) {
                MethodInfo implementation = info.methods.get(method.name());
                if (implementation != null && iface.declaration.methods().contains(method) && !methodSignatureCompatible(info, t, method, implementation.declaration)) fail("C227", "method '" + method.name() + "' does not match the implemented trait signature", implementation.declaration.span());
                if (method.body() == null && implementation == null && !delegates(info, method.name())) fail("C124", "struct '" + owner + "' does not implement '" + method.name() + "'", info.declaration.span());
                if (method.body() != null && !info.methods.containsKey(method.name())) {
                    MethodDecl previous = defaults.putIfAbsent(method.name(), method);
                    if (previous != null && !previous.equals(method)) fail("C226", "conflicting default methods for '" + method.name() + "'", info.declaration.span());
                }
            }
        }
    }
    private void checkTrait(TraitInfo info) { owner = info.declaration.name(); for (MethodDecl m : info.declaration.methods()) checkMethod(null, m); }
    private boolean delegates(StructInfo info, String method) {
        Set<String> delegated = delegatedMethodCache.get(info);
        if (delegated == null) {
            delegated = new HashSet<>();
            for (DelegateDecl delegate : info.declaration.delegates()) {
                TraitInfo iface = traits.get(delegate.traitType().name());
                if (iface != null) for (MethodDecl candidate : traitMethods(iface)) delegated.add(candidate.name());
            }
            delegatedMethodCache.put(info, delegated);
        }
        return delegated.contains(method);
    }

    private boolean methodSignatureCompatible(StructInfo struct, Type traitType, MethodDecl expected, MethodDecl actual) {
        if (!expected.instance() || !actual.instance() || expected.params().size() != actual.params().size()) return false;
        Map<String, Type> traitSubstitutions = new HashMap<>(); TraitDecl iface = traits.get(traitType.name()).declaration;
        for (int i = 0; i < iface.typeParams().size() && i < traitType.args().size(); i++) traitSubstitutions.put(iface.typeParams().get(i).name(), traitType.args().get(i));
        Map<String, Type> structSubstitutions = new HashMap<>(); for (TypeParam p : struct.declaration.typeParams()) structSubstitutions.put(p.name(), var(p.name()));
        for (int i = 0; i < expected.params().size(); i++) if (!parameterCompatible(applyTypeRef(expected.params().get(i).type(), traitSubstitutions), applyTypeRef(actual.params().get(i).type(), structSubstitutions))) return false;
        return returnCompatible(applyTypeRef(expected.returnType(), traitSubstitutions), applyTypeRef(actual.returnType(), structSubstitutions));
    }

    private boolean parameterCompatible(Type expected, Type actual) { return sameIgnoringNull(expected, actual) || (!expected.nullable() && actual.nullable() && expected.base() == actual.base() && expected.name().equals(actual.name()) && expected.args().equals(actual.args())); }
    private boolean returnCompatible(Type expected, Type actual) { return sameIgnoringNull(expected, actual) || (expected.nullable() && !actual.nullable() && expected.base() == actual.base() && expected.name().equals(actual.name()) && expected.args().equals(actual.args())); }

    private void checkDelegates(StructInfo info) {
        Set<String> seenTraits = new HashSet<>(); Map<String, String> methods = new HashMap<>();
        Type self = generic(Base.STRUCT, info.declaration.name(), info.declaration.typeParams().stream().map(p -> var(p.name())).toList());
        for (DelegateDecl delegate : info.declaration.delegates()) {
            Map<String, Type> substitutions = new HashMap<>(); for (TypeParam parameter : info.declaration.typeParams()) substitutions.put(parameter.name(), var(parameter.name()));
            Type target = applyTypeRef(delegate.traitType(), substitutions);
            TraitInfo iface = target.base() == Base.TRAIT ? traits.get(target.name()) : null;
            if (iface == null) { fail("C123", "'" + delegate.traitType().name() + "' is not a trait", delegate.span()); continue; }
            if (!seenTraits.add(target.name())) { fail("C221", "trait '" + target.name() + "' is already delegated", delegate.span()); continue; }
            if (!conformsToTrait(self, target)) { fail("C220", "struct '" + info.declaration.name() + "' delegates trait '" + target.name() + "' which is not in its 'implements' list", delegate.span()); continue; }
            FieldInfo field = info.fields.get(delegate.field());
            if (field == null) { fail("C222", "delegate target field '" + delegate.field() + "' does not exist in struct '" + info.declaration.name() + "'", delegate.span()); continue; }
            if (field.declaration.isStatic()) { fail("C232", "delegate target '" + delegate.field() + "' is a static field; delegation requires an instance field", delegate.span()); continue; }
            if (field.declaration.type().nullable() || field.type.nullable()) { fail("C223", "delegate target field '" + delegate.field() + "' must be non-nullable", delegate.span()); continue; }
            if (!conformsToTrait(field.type, target)) { fail("C224", "delegate target '" + delegate.field() + "' of type " + field.type + " does not implement trait '" + target.name() + "'", delegate.span()); continue; }
            for (MethodDecl method : traitMethods(iface)) {
                if (info.methods.containsKey(method.name())) continue;
                String previous = methods.putIfAbsent(method.name(), delegate.field());
                if (previous != null && !previous.equals(delegate.field())) fail("C225", "method '" + method.name() + "' has conflicting delegated implementations from fields '" + previous + "' and '" + delegate.field() + "'; declare it explicitly", delegate.span());
            }
        }
    }

    private List<MethodDecl> traitMethods(TraitInfo iface) {
        List<MethodDecl> cached = traitMethodCache.get(iface.declaration.name());
        if (cached != null) return cached;
        List<MethodDecl> methods = new ArrayList<>(iface.declaration.methods());
        for (TypeRef parent : iface.declaration.extendsTypes()) { TraitInfo inherited = traits.get(parent.name()); if (inherited != null) for (MethodDecl method : traitMethods(inherited)) if (methods.stream().noneMatch(x -> x.name().equals(method.name()))) methods.add(method); }
        List<MethodDecl> result = List.copyOf(methods);
        traitMethodCache.put(iface.declaration.name(), result);
        return result;
    }

    private Type applyTypeRef(TypeRef ref, Map<String, Type> substitutions) {
        List<TypeRef> refArguments = ref.args();
        if (substitutions.containsKey(ref.name()) && refArguments.isEmpty()) { Type value = substitutions.get(ref.name()); return ref.nullable() ? new Type(value.base(), value.name(), value.args(), true) : value; }
        List<Type> args = applyTypeRefs(refArguments, substitutions); Type result = fromName(ref.name(), args, ref.nullable());
        if (result.base() == Base.UNKNOWN) {
            if (structs.containsKey(ref.name())) result = new Type(Base.STRUCT, ref.name(), args, ref.nullable());
            else if (traits.containsKey(ref.name())) result = new Type(Base.TRAIT, ref.name(), args, ref.nullable());
            else if (enums.containsKey(ref.name())) result = new Type(Base.ENUM, ref.name(), args, ref.nullable());
        }
        return result;
    }

    /**
     * Apply substitutions to a type-reference argument list with a plain loop.
     * Most type references have no arguments, so short-circuiting the empty list
     * avoids a stream, a lambda, and an empty-list allocation per reference
     * during semantic analysis.
     */
    private List<Type> applyTypeRefs(List<TypeRef> refs, Map<String, Type> substitutions) {
        if (refs.isEmpty()) return List.of();
        List<Type> result = new ArrayList<>(refs.size());
        for (TypeRef ref : refs) result.add(applyTypeRef(ref, substitutions));
        return result;
    }

    /** Resolve a type-reference argument list against an applied generic type with a plain loop. */
    private List<Type> resolveWithArgumentRefs(List<TypeRef> refs, Type applied) {
        if (refs.isEmpty()) return List.of();
        List<Type> result = new ArrayList<>(refs.size());
        for (TypeRef ref : refs) result.add(resolveWithArguments(ref, applied));
        return result;
    }

    private boolean conformsToTrait(Type source, Type target) {
        if (target == null || target.base() != Base.TRAIT) return false;
        if (source == null) return false;
        if (source.base() == Base.TYPE_VAR) {
            for (StructInfo info : structs.values()) for (TypeParam parameter : info.declaration.typeParams()) if (parameter.name().equals(source.name())) {
                Map<String, Type> substitutions = new HashMap<>(); for (TypeParam p : info.declaration.typeParams()) substitutions.put(p.name(), var(p.name()));
                for (TypeRef constraint : parameter.constraints()) if (traitConforms(applyTypeRef(constraint, substitutions), target, new HashSet<>())) return true;
            }
            return false;
        }
        if (source.base() == Base.TRAIT) return traitConforms(source, target, new HashSet<>());
        if (source.base() != Base.STRUCT || !structs.containsKey(source.name())) return false;
        StructDecl declaration = structs.get(source.name()).declaration; Map<String, Type> substitutions = new HashMap<>();
        for (int i = 0; i < declaration.typeParams().size() && i < source.args().size(); i++) substitutions.put(declaration.typeParams().get(i).name(), source.args().get(i));
        for (TypeRef implementation : declaration.implementsTypes()) if (traitConforms(applyTypeRef(implementation, substitutions), target, new HashSet<>())) return true;
        return false;
    }

    private boolean traitConforms(Type source, Type target, Set<String> path) {
        if (source == null || source.base() != Base.TRAIT || !traits.containsKey(source.name())) return false;
        if (source.name().equals(target.name()) && compatibleTypeArguments(source, target)) return true;
        String key = source.toString() + " -> " + target; if (!path.add(key)) return false;
        TraitDecl declaration = traits.get(source.name()).declaration; Map<String, Type> substitutions = new HashMap<>();
        for (int i = 0; i < declaration.typeParams().size() && i < source.args().size(); i++) substitutions.put(declaration.typeParams().get(i).name(), source.args().get(i));
        for (TypeRef parent : declaration.extendsTypes()) if (traitConforms(applyTypeRef(parent, substitutions), target, path)) return true;
        return false;
    }

    private boolean compatibleTypeArguments(Type source, Type target) {
        if (source.args().isEmpty() || target.args().isEmpty()) return true;
        return source.args().size() == target.args().size() && source.args().equals(target.args());
    }

    private void checkMethod(StructInfo struct, MethodDecl method) {
        currentMethod = method; staticContext = !method.instance(); typeVariables.clear();
        if (struct != null) for (TypeParam p : struct.declaration.typeParams()) typeVariables.put(p.name(), var(p.name()));
        else if (traits.containsKey(owner)) for (TypeParam p : traits.get(owner).declaration.typeParams()) typeVariables.put(p.name(), var(p.name()));
        for (TypeParam p : method.typeParams()) typeVariables.put(p.name(), var(p.name()));
        push();
        if (method.instance()) declare("self", named(struct == null ? Base.TRAIT : Base.STRUCT, owner));
        for (Param p : method.params()) declare(p.name(), p.variadic() ? generic(Base.LIST, "List", List.of(resolve(p.type(), owner))) : resolve(p.type(), owner));
        if (method.body() != null) { checkBlock(method.body()); Type result = resolve(method.returnType(), owner); if (result.base() != Base.VOID && !blockReturns(method.body())) fail("C130", "value-returning method may fall off the end", method.span()); }
        pop();
    }

    private boolean blockReturns(Block block) { boolean diverged = false; for (Stmt statement : block.statements()) if (!diverged) diverged = statementReturns(statement); return diverged; }
    private boolean statementReturns(Stmt statement) {
        if (statement instanceof ReturnStmt || statement instanceof ThrowStmt) return true;
        if (statement instanceof BlockStmt b) return blockReturns(b.block());
        if (statement instanceof IfStmt i) return blockReturns(i.thenBlock()) && i.elseBranch() != null && statementReturns(i.elseBranch());
        if (statement instanceof TryStmt t) { if (t.finallyBlock() != null && blockReturns(t.finallyBlock())) return true; return blockReturns(t.body()) && t.catches().stream().allMatch(c -> blockReturns(c.body())); }
        return false;
    }

    private void checkEntryPoint() {
        StructInfo main = structs.get("Main"); if (main == null) { fail("C130", "entry struct 'Main' is missing", Span.synthetic(model.unit.span().file())); return; }
        MethodInfo run = main.methods.get("run"); if (run == null || run.declaration.instance() || run.declaration.params().size() != 1 || !run.declaration.params().get(0).variadic() || resolve(run.declaration.returnType(), "Main").base() != Base.INTEGER) fail("C131", "Main.run must be a static variadic String method returning Integer", main.declaration.span());
    }

    private void checkBlock(Block block) {
        push(); boolean diverged = false;
        for (Stmt s : block.statements()) {
            if (diverged) { fail("C245", "unreachable statement", s.span()); continue; }
            checkStmt(s); diverged = statementDiverges(s);
        }
        pop();
    }

    private boolean statementDiverges(Stmt statement) {
        if (statement instanceof ReturnStmt || statement instanceof ThrowStmt || statement instanceof BreakStmt || statement instanceof ContinueStmt) return true;
        if (statement instanceof BlockStmt b) return blockDiverges(b.block());
        if (statement instanceof IfStmt i) return blockDiverges(i.thenBlock()) && i.elseBranch() != null && statementDiverges(i.elseBranch());
        if (statement instanceof TryStmt t) return t.finallyBlock() != null && blockDiverges(t.finallyBlock());
        return false;
    }

    private boolean blockDiverges(Block block) {
        boolean diverged = false;
        for (Stmt statement : block.statements()) if (!diverged) diverged = statementDiverges(statement);
        return diverged;
    }
    private void checkStmt(Stmt stmt) {
        if (stmt instanceof VarDecl d) { Type t = resolve(d.type(), owner); if (d.initializer() != null) checkAssignable(checkExpr(d.initializer(), t), t, d.initializer(), "initializer"); declare(d.name(), t, d.mutable(), d.initializer() != null); }
        else if (stmt instanceof ExprStmt e) checkExpr(e.expression(), null);
        else if (stmt instanceof ReturnStmt r) { Type expected = currentMethod == null ? named(Base.VOID, "Void") : resolve(currentMethod.returnType(), owner); if (r.value() == null) { if (expected.base() != Base.VOID) fail("C141", "return value is required", r.span()); } else checkAssignable(checkExpr(r.value(), expected), expected, r.value(), "return value"); }
        else if (stmt instanceof IfStmt i) checkIf(i);
        else if (stmt instanceof WhileStmt w) { checkBoolean(w.condition()); int before = assignedSnapshot(); checkBlock(w.body()); restoreAssigned(before); }
        else if (stmt instanceof ForStmt f) { Type it = checkExpr(f.iterable(), null); Type element = iterableElement(it); int before = assignedSnapshot(); push(); declare(f.name(), element, true); checkBlock(f.body()); pop(); restoreAssigned(before); }
        else if (stmt instanceof SwitchStmt s) { Type subject = checkExpr(s.subject(), null); for (SwitchCase c : s.cases()) { for (Expr v : c.values()) checkAssignable(checkExpr(v, subject), subject, v, "switch case"); checkBlock(c.body()); } }
        else if (stmt instanceof TryStmt t) checkTry(t);
        else if (stmt instanceof ThrowStmt t) { Type x = checkExpr(t.value(), null); if (x.base() == Base.VOID || x.base() == Base.NULL) fail("C150", "throw requires a value", t.span()); }
        else if (stmt instanceof BlockStmt b) checkBlock(b.block());
    }

    private void checkIf(IfStmt statement) {
        checkBoolean(statement.condition()); int before = assignedSnapshot();
        narrowingScopes.add(new HashMap<>());
        narrowFromNullTest(statement.condition(), true);
        checkBlock(statement.thenBlock());
        List<AssignKey> thenAdds = statement.elseBranch() == null ? null : copyAdditions(before, assignedSnapshot());
        restoreAssigned(before);
        narrowingScopes.remove(narrowingScopes.size() - 1);
        narrowingScopes.add(new HashMap<>());
        narrowFromNullTest(statement.condition(), false);
        if (statement.elseBranch() != null) {
            checkStmt(statement.elseBranch());
            List<AssignKey> elseAdds = copyAdditions(before, assignedSnapshot());
            restoreAssigned(before);
            applyAssignments(commonAssignments(thenAdds, elseAdds));
        }
        narrowingScopes.remove(narrowingScopes.size() - 1);
    }

    private void narrowFromNullTest(Expr condition, boolean thenBranch) {
        if (!(condition instanceof BinaryExpr binary) || (binary.op() != BinaryOp.EQ && binary.op() != BinaryOp.NE)) return;
        NameExpr name = null;
        if (binary.left() instanceof NameExpr n && binary.right() instanceof Literal l && l.kind() == LiteralKind.NULL) name = n;
        if (binary.right() instanceof NameExpr n && binary.left() instanceof Literal l && l.kind() == LiteralKind.NULL) name = n;
        if (name == null) return;
        boolean nonNull = binary.op() == BinaryOp.NE ? thenBranch : !thenBranch;
        Type original = lookupNameType(name);
        if (original.nullable()) narrowingScopes.get(narrowingScopes.size() - 1).put(name.name(), nonNull ? original.nonNull() : new Type(Base.NULL, "null", List.of(), true));
    }

    private void checkTry(TryStmt statement) {
        int before = assignedSnapshot(); checkBlock(statement.body());
        List<AssignKey> merged = statement.catches().isEmpty() ? null : copyAdditions(before, assignedSnapshot());
        for (CatchClause clause : statement.catches()) {
            restoreAssigned(before); Type catchType = resolve(clause.type(), owner); push(); declare(clause.name(), catchType, false, true); checkBlock(clause.body()); pop();
            merged = commonAssignments(merged, copyAdditions(before, assignedSnapshot()));
        }
        restoreAssigned(before);
        if (merged != null) applyAssignments(merged);
        if (statement.finallyBlock() != null) checkBlock(statement.finallyBlock());
    }

    /** A definite-assignment snapshot is the size of the assignment undo log. */
    private int assignedSnapshot() { return assignLogSets.size(); }

    /** Undoes every assignment recorded after {@code snapshot}. */
    private void restoreAssigned(int snapshot) {
        while (assignLogSets.size() > snapshot) {
            int i = assignLogSets.size() - 1;
            String name = assignLogNames.remove(i);
            Set<String> set = assignLogSets.remove(i);
            set.remove(name);
        }
    }

    /** Copies the additions recorded in {@code [from, to)} of the assignment log. */
    private List<AssignKey> copyAdditions(int from, int to) {
        ArrayList<AssignKey> result = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) result.add(new AssignKey(assignLogSets.get(i), assignLogNames.get(i)));
        return result;
    }

    /** The additions present in both lists, in {@code left}'s order, deduplicated. */
    private List<AssignKey> commonAssignments(List<AssignKey> left, List<AssignKey> right) {
        HashSet<AssignKey> rightKeys = new HashSet<>(right.size() * 2);
        for (AssignKey key : right) rightKeys.add(key);
        ArrayList<AssignKey> result = new ArrayList<>();
        HashSet<AssignKey> seen = new HashSet<>();
        for (AssignKey key : left) if (rightKeys.contains(key) && seen.add(key)) result.add(key);
        return result;
    }

    /** Re-applies recorded additions to the current (restored) scope state. */
    private void applyAssignments(List<AssignKey> additions) {
        for (AssignKey key : additions) markAssignedIn(key.set, key.name);
    }

    /** Records one definite assignment in the undo log; re-assignments of an already-assigned name are no-ops. */
    private void markAssignedIn(Set<String> set, String name) {
        if (set.add(name)) {
            assignLogSets.add(set);
            assignLogNames.add(name);
        }
    }

    /** Identity key for an undo-log entry: a scope's set object plus a variable name. */
    private static final class AssignKey {
        final Set<String> set; final String name;
        AssignKey(Set<String> set, String name) { this.set = set; this.name = name; }
        @Override public boolean equals(Object other) { return other instanceof AssignKey k && k.set == set && k.name.equals(name); }
        @Override public int hashCode() { return System.identityHashCode(set) * 31 + name.hashCode(); }
    }

    private Type iterableElement(Type it) {
        if (it.base() == Base.LIST || it.base() == Base.STACK || it.base() == Base.SET) return it.args().isEmpty() ? named(Base.OBJECT, "Object") : it.args().get(0);
        if (it.base() == Base.MAP) return it.args().isEmpty() ? named(Base.OBJECT, "Object") : it.args().get(0);
        if (it.base() == Base.STRING) return named(Base.CHAR, "Char");
        if (it.base() == Base.RANGE) return named(Base.LONG, "Long");
        fail("C160", "value is not iterable", model.unit.span()); return named(Base.OBJECT, "Object");
    }
    private void checkBoolean(Expr e) { if (checkExpr(e, null).base() != Base.BOOLEAN) fail("C161", "condition must be Boolean", e.span()); }

    public Type checkExpr(Expr e, Type expected) {
        Type result;
        if (e instanceof Literal l) result = literalType(l);
        else if (e instanceof NameExpr n) result = nameType(n);
        else if (e instanceof ListExpr l) { Type elem = expected != null && expected.base() == Base.LIST && !expected.args().isEmpty() ? expected.args().get(0) : null; for (Expr x : l.elements()) { Type xt = checkExpr(x, elem); if (elem != null) checkAssignable(xt, elem, x, "list element"); } result = generic(Base.LIST, "List", List.of(elem == null ? commonTypes(l.elements()) : elem)); }
        else if (e instanceof MapExpr m) { Type kt = expected != null && expected.base() == Base.MAP ? expected.args().get(0) : null; Type vt = expected != null && expected.base() == Base.MAP ? expected.args().get(1) : null; for (MapEntry x : m.entries()) { Type a = checkExpr(x.key(), kt), b = checkExpr(x.value(), vt); if (kt != null) checkAssignable(a, kt, x.key(), "map key"); if (vt != null) checkAssignable(b, vt, x.value(), "map value"); } result = generic(Base.MAP, "Map", List.of(kt == null ? named(Base.OBJECT, "Object") : kt, vt == null ? named(Base.OBJECT, "Object") : vt)); }
        else if (e instanceof StaticExpr s) result = staticValueType(s);
        else if (e instanceof MemberExpr m) result = memberType(m);
        else if (e instanceof CallExpr c) result = callType(c);
        else if (e instanceof UnaryExpr u) { Type x = checkExpr(u.operand(), null); result = u.op() == UnaryOp.NOT ? named(Base.BOOLEAN, "Boolean") : unaryNumeric(x); if (result == null) fail("C162", "invalid unary operand", u.span()); }
        else if (e instanceof BinaryExpr b) result = binaryType(b);
        else if (e instanceof CoalesceExpr c) { Type l = checkExpr(c.left(), null); Type r = checkExpr(c.right(), l.nonNull()); if (l.nullable()) { checkAssignable(r, l.nonNull(), c.right(), "coalesce fallback"); result = l.nonNull(); } else { fail("C163", "left operand of ?? must be nullable", c.span()); result = r; } }
        else if (e instanceof SelfInitExpr s) { StructInfo info = structs.get(owner); if (info == null) { fail("C164", "Self construction outside a struct", s.span()); result = named(Base.OBJECT, "Object"); } else { Set<String> names = new HashSet<>(); for (FieldInit f : s.fields()) { FieldInfo field = info.fields.get(f.name()); if (field == null) fail("C165", "unknown field '" + f.name() + "'", f.span()); else { names.add(f.name()); checkAssignable(checkExpr(f.value(), field.type), field.type, f.value(), "field initializer"); } } result = named(Base.STRUCT, owner); } }
        else if (e instanceof AssignExpr a) { Type target = lvalueType(a.target()); Type value = checkExpr(a.value(), target); checkAssignable(value, target, a.value(), "assignment"); result = target; }
        else if (e instanceof UpdateExpr u) { Type target = lvalueType(u.target()); Type value = checkExpr(u.value(), null); if (u.op() == BinaryOp.CONCAT) { if (target.base() != Base.STRING) fail("C155", "'..=' requires a String target", u.span()); } else if (binaryPromotion(target, value) == null) fail("C166", "invalid compound assignment", u.span()); result = target; }
        else if (e instanceof RangeExpr r) result = named(Base.RANGE, "Range");
        else if (e instanceof MatchExpr m) result = matchType(m);
        else result = named(Base.UNKNOWN, "Unknown");
        model.expressionTypes.put(e, result);
        if (expected != null) checkAssignable(result, expected, e, "expression");
        return result;
    }

    private Type matchType(MatchExpr m) { Type s = checkExpr(m.subject(), null); Type result = null; for (MatchArm a : m.arms()) { push(); checkPattern(a.pattern(), s); if (a.guard() != null) checkBoolean(a.guard()); Type t = checkExpr(a.body(), result); pop(); if (result == null) result = t; else if (!assignable(t, result, a.body())) result = common(result, t); } return result == null ? named(Base.VOID, "Void") : result; }
    private void checkPattern(Pattern p, Type subject) {
        if (p instanceof BindPattern b) declare(b.name(), subject);
        else if (p instanceof LiteralPattern l) checkAssignable(literalType(l.literal()), subject, l.literal(), "pattern");
        else if (p instanceof ListPattern l) { if (subject.base() != Base.LIST) fail("C167", "list pattern requires a List subject", l.span()); else if (subject.nullable()) fail("C219", "list patterns require a non-null List subject", l.span()); for (Pattern x : l.elements()) checkPattern(x, subject.args().isEmpty() ? named(Base.OBJECT, "Object") : subject.args().get(0)); }
        else if (p instanceof VariantPattern v) {
            if (subject.base() != Base.ENUM) fail("C168", "variant pattern requires an enum subject", v.span());
            else if (subject.nullable()) fail("C219", "variant patterns require a non-null enum subject", v.span());
            if (subject.base() == Base.ENUM && enums.containsKey(subject.name())) for (VariantDecl variant : enums.get(subject.name()).declaration.variants()) if (variant.name().equals(v.name()) && variant.payload() != null) for (Pattern x : v.payload()) checkPattern(x, resolveWithArguments(variant.payload(), subject));
        }
    }

    private Type resolveWithArguments(TypeRef ref, Type applied) {
        if (ref == null) return named(Base.VOID, "Void");
        if (applied != null && enums.containsKey(applied.name())) {
            List<TypeParam> params = enums.get(applied.name()).declaration.typeParams();
            for (int i = 0; i < params.size() && i < applied.args().size(); i++) if (params.get(i).name().equals(ref.name())) return applied.args().get(i);
        }
        List<Type> args = resolveWithArgumentRefs(ref.args(), applied);
        Type t = fromName(ref.name(), args, ref.nullable());
        if (t.base() == Base.UNKNOWN) {
            if (structs.containsKey(ref.name())) t = new Type(Base.STRUCT, ref.name(), args, ref.nullable());
            else if (traits.containsKey(ref.name())) t = new Type(Base.TRAIT, ref.name(), args, ref.nullable());
            else if (enums.containsKey(ref.name())) t = new Type(Base.ENUM, ref.name(), args, ref.nullable());
            else if (typeVariables.containsKey(ref.name())) t = typeVariables.get(ref.name());
        }
        return t;
    }

    private Type binaryType(BinaryExpr b) {
        Type a = checkExpr(b.left(), null), c = checkExpr(b.right(), null);
        return switch (b.op()) {
            case AND, OR -> { if (a.base() != Base.BOOLEAN || c.base() != Base.BOOLEAN) fail("C169", "logical operands must be Boolean", b.span()); yield named(Base.BOOLEAN, "Boolean"); }
            case CONCAT -> { if (a.base() == Base.VOID || c.base() == Base.VOID) { fail("C155", "'..' requires value operands", b.span()); yield named(Base.STRING, "String"); } if (a.base() == Base.STRING || c.base() == Base.STRING || a.base() == Base.OBJECT || c.base() == Base.OBJECT) yield named(Base.STRING, "String"); if (a.integral() && c.integral()) yield named(Base.RANGE, "Range"); fail("C155", "'..' requires a String operand", b.span()); yield named(Base.STRING, "String"); }
            case ADD, SUB, MUL, DIV, MOD -> { Type p = binaryPromotion(a, c); if (p == null) fail("C170", "operands are not numerically compatible", b.span()); yield p == null ? named(Base.INTEGER, "Integer") : p; }
            case EQ, NE -> { if (a.numeric() && c.numeric() && binaryPromotion(a, c) == null) fail("C171", "numeric operands are not compatible", b.span()); yield named(Base.BOOLEAN, "Boolean"); }
            case LT, LE, GT, GE -> { if (binaryPromotion(a, c) == null && !(a.base() == Base.STRING && c.base() == Base.STRING)) fail("C172", "operands are not comparable", b.span()); yield named(Base.BOOLEAN, "Boolean"); }
        };
    }

    private Type callType(CallExpr call) {
        if (call.callee() instanceof StaticExpr s) {
            Type ownerType = resolve(s.type(), owner); CallInfo info = staticCall(ownerType, s.name(), call.arguments(), call.span()); model.calls.put(call, info); return info.result();
        }
        if (call.callee() instanceof MemberExpr m) {
            Type receiver = checkExpr(m.object(), null); CallInfo info = instanceCall(receiver, m.name(), call.arguments(), call.span()); model.calls.put(call, info); return info.result();
        }
        if (call.callee() instanceof NameExpr n) {
            MethodInfo method = owner == null ? null : (structs.containsKey(owner) ? structs.get(owner).methods.get(n.name()) : traits.containsKey(owner) ? traitMethod(traits.get(owner), n.name()) : null);
            if (method != null) { Type receiver = method.declaration.instance() ? named(structs.containsKey(owner) ? Base.STRUCT : Base.TRAIT, owner) : null; CallInfo info = bindCall(method, receiver, call.arguments(), call.span()); model.calls.put(call, info); return info.result(); }
        }
        fail("C173", "value is not callable", call.span()); return named(Base.OBJECT, "Object");
    }

    private CallInfo staticCall(Type type, String name, List<Arg> args, Span span) {
        String builtin = builtinStatic(type, name); if (builtin != null) { Type ret = builtinReturn(type, name, args); for (Arg a : args) checkExpr(a.expression(), null); return new CallInfo(ret, null, args.stream().map(Arg::expression).toList(), false, builtin, type); }
        if (type.base() == Base.ENUM && enums.containsKey(type.name())) { VariantDecl v = enums.get(type.name()).declaration.variants().stream().filter(x -> x.name().equals(name)).findFirst().orElse(null); if (v != null) { if (v.payload() != null && args.size() != 1) fail("C174", "enum payload arity mismatch", span); for (Arg a : args) checkExpr(a.expression(), null); return new CallInfo(type, null, args.stream().map(Arg::expression).toList(), false, "enum:" + name, type); } }
        StructInfo s = structs.get(type.name()); if (s != null) { MethodInfo m = s.methods.get(name); if (m != null && !m.declaration.instance()) return bindCall(m, type, args, span); }
        fail("C175", "unknown static member '" + type.name() + "." + name + "'", span); return new CallInfo(named(Base.OBJECT, "Object"), null, List.of(), false, "", type);
    }
    private CallInfo instanceCall(Type receiver, String name, List<Arg> args, Span span) {
        if ((name.equals("toString") || name.equals("equals") || name.equals("hashCode")) && !receiver.primitive()) { for (Arg a : args) checkExpr(a.expression(), null); Type ret = name.equals("toString") ? named(Base.STRING, "String") : name.equals("hashCode") ? named(Base.LONG, "Long") : named(Base.BOOLEAN, "Boolean"); return new CallInfo(ret, null, args.stream().map(Arg::expression).toList(), false, "object:" + name, receiver); }
        String builtin = builtinInstance(receiver, name); if (builtin != null) { Type ret = builtinReturn(receiver, name, args); checkBuiltinArguments(receiver, name, args); return new CallInfo(ret, null, args.stream().map(Arg::expression).toList(), false, builtin, receiver); }
        MethodInfo method = null; if (receiver.base() == Base.STRUCT && structs.containsKey(receiver.name())) method = structs.get(receiver.name()).methods.get(name); else if (receiver.base() == Base.TRAIT && traits.containsKey(receiver.name())) method = traitMethod(traits.get(receiver.name()), name);
        Type dispatchReceiver = receiver;
        if (method == null && receiver.base() == Base.STRUCT && structs.containsKey(receiver.name())) {
            StructInfo struct = structs.get(receiver.name());
            for (TypeRef implemented : struct.declaration.implementsTypes()) {
                TraitInfo iface = traits.get(implemented.name());
                MethodInfo candidate = iface == null ? null : traitMethod(iface, name);
                if (candidate != null) { method = candidate; dispatchReceiver = resolve(implemented, struct.declaration.name()); break; }
            }
            if (method == null) for (DelegateDecl delegate : struct.declaration.delegates()) {
                TraitInfo iface = traits.get(delegate.traitType().name());
                MethodInfo candidate = iface == null ? null : traitMethod(iface, name);
                if (candidate != null) { method = candidate; dispatchReceiver = resolve(delegate.traitType(), struct.declaration.name()); break; }
            }
        }
        if (method != null && method.declaration.instance()) return bindCall(method, dispatchReceiver, args, span);
        if ((name.equals("toString") || name.equals("equals") || name.equals("hashCode")) && !receiver.primitive()) { for (Arg a : args) checkExpr(a.expression(), null); Type ret = name.equals("toString") ? named(Base.STRING, "String") : name.equals("hashCode") ? named(Base.LONG, "Long") : named(Base.BOOLEAN, "Boolean"); return new CallInfo(ret, null, args.stream().map(Arg::expression).toList(), false, "object:" + name, receiver); }
        if (receiver.base() == Base.OBJECT) { for (Arg a : args) checkExpr(a.expression(), null); return new CallInfo(named(Base.OBJECT, "Object"), null, args.stream().map(Arg::expression).toList(), false, "dynamic:" + name, receiver); }
        fail("C176", "unknown instance member '" + name + "' on " + receiver, span); return new CallInfo(named(Base.OBJECT, "Object"), null, List.of(), false, "", receiver);
    }

    private MethodInfo traitMethod(TraitInfo iface, String name) {
        MethodInfo own = iface.methods.get(name);
        if (own != null) return own;
        for (TypeRef parent : iface.declaration.extendsTypes()) {
            TraitInfo inherited = traits.get(parent.name());
            if (inherited != null) { MethodInfo found = traitMethod(inherited, name); if (found != null) return found; }
        }
        return null;
    }

    private CallInfo bindCall(MethodInfo method, Type receiver, List<Arg> args, Span span) {
        MethodDecl m = method.declaration; List<Arg> positional = new ArrayList<>(); Map<String, Expr> named = new HashMap<>();
        for (Arg a : args) { if (a.name() == null) positional.add(a); else named.put(a.name(), a.expression()); }
        Map<String, Type> inferred = new HashMap<>();
        List<Expr> actual = new ArrayList<>(); int supplied = 0;
        for (Param p : m.params()) {
            if (p.variadic()) {
                while (supplied < positional.size()) {
                    Arg argument = positional.get(supplied++); Expr x = argument.expression();
                    Type actualType = checkExpr(x, null);
                    if (argument.spread()) {
                        Type element = iterableElement(actualType);
                        Type target = resolveCallType(p.type(), method, receiver, inferred);
                        checkAssignable(element, target, x, "spread argument");
                        actual.add(x);
                        continue;
                    }
                    actual.add(x);
                    collectInference(p.type(), actualType, method, inferred);
                    Type target = resolveCallType(p.type(), method, receiver, inferred);
                    checkAssignable(actualType, target, x, "variadic argument");
                }
                continue;
            }
            Expr x = named.get(p.name()); if (x == null && supplied < positional.size()) x = positional.get(supplied++).expression(); if (x == null) x = p.defaultValue();
            if (x == null) { fail("C177", "missing argument '" + p.name() + "'", span); continue; }
            Type actualType = checkExpr(x, null);
            collectInference(p.type(), actualType, method, inferred);
            Type target = resolveCallType(p.type(), method, receiver, inferred);
            checkAssignable(actualType, target, x, "argument"); actual.add(x);
        }
        Type result = resolveCallType(m.returnType(), method, receiver, inferred);
        return new CallInfo(result, method, actual, m.params().stream().anyMatch(Param::variadic), null, receiver);
    }

    private void collectInference(TypeRef ref, Type actual, MethodInfo method, Map<String, Type> inferred) {
        if (ref == null || actual == null) return;
        boolean methodTypeParameter = method.declaration.typeParams().stream().anyMatch(p -> p.name().equals(ref.name()));
        boolean ownerTypeParameter = (structs.containsKey(method.owner) && structs.get(method.owner).declaration.typeParams().stream().anyMatch(p -> p.name().equals(ref.name())))
                || (traits.containsKey(method.owner) && traits.get(method.owner).declaration.typeParams().stream().anyMatch(p -> p.name().equals(ref.name())));
        if ((methodTypeParameter || ownerTypeParameter) && ref.args().isEmpty()) {
            inferred.putIfAbsent(ref.name(), actual);
            return;
        }
        List<Type> actualArgs = actual.args();
        for (int i = 0; i < ref.args().size() && i < actualArgs.size(); i++) collectInference(ref.args().get(i), actualArgs.get(i), method, inferred);
    }

    private Type resolveCallType(TypeRef ref, MethodInfo method, Type receiver, Map<String, Type> inferred) {
        if (ref == null) return named(Base.VOID, "Void");
        String n = ref.name();

        Map<String, Type> receiverTypes = new HashMap<>();
        if (receiver != null) {
            List<TypeParam> parameters = method.owner != null && structs.containsKey(method.owner)
                    ? structs.get(method.owner).declaration.typeParams()
                    : traits.containsKey(method.owner) ? traits.get(method.owner).declaration.typeParams() : List.of();
            for (int i = 0; i < parameters.size() && i < receiver.args().size(); i++) receiverTypes.put(parameters.get(i).name(), receiver.args().get(i));
        }
        if (n.equals("Self") && receiver != null) {
            if (!receiver.args().isEmpty() || receiverTypes.isEmpty()) return receiver;
            List<Type> arguments = new ArrayList<>();
            List<TypeParam> parameters = structs.containsKey(method.owner) ? structs.get(method.owner).declaration.typeParams() : List.of();
            for (TypeParam parameter : parameters) arguments.add(inferred.getOrDefault(parameter.name(), new Type(Base.TYPE_VAR, parameter.name(), List.of(), false)));
            return arguments.isEmpty() ? receiver : generic(receiver.base(), receiver.name(), arguments);
        }
        Type substituted = receiverTypes.get(n);
        if (substituted == null) substituted = inferred.get(n);
        if (substituted != null) return ref.nullable() ? new Type(substituted.base(), substituted.name(), substituted.args(), true) : substituted;
        if (method.declaration.typeParams().stream().anyMatch(p -> p.name().equals(n))) return new Type(Base.TYPE_VAR, n, List.of(), ref.nullable());

        String current = receiver == null ? method.owner : receiver.name();
        String resolvedName = n.equals("Self") ? current : n;
        List<Type> arguments = ref.args().stream().map(x -> resolveCallType(x, method, receiver, inferred)).toList();
        Type result = fromName(resolvedName, arguments, ref.nullable());
        if (result.base() == Base.UNKNOWN) {
            if (structs.containsKey(resolvedName)) result = new Type(Base.STRUCT, resolvedName, arguments, ref.nullable());
            else if (traits.containsKey(resolvedName)) result = new Type(Base.TRAIT, resolvedName, arguments, ref.nullable());
            else if (enums.containsKey(resolvedName)) result = new Type(Base.ENUM, resolvedName, arguments, ref.nullable());
            else if (typeVariables.containsKey(resolvedName)) result = typeVariables.get(resolvedName);
            else fail("C189", "unknown type '" + resolvedName + "'", ref.span());
        }
        return result;
    }

    private Type staticValueType(StaticExpr s) {
        Type t = resolve(s.type(), owner); if (t.base() == Base.ENUM && enums.containsKey(t.name())) { for (VariantDecl v : enums.get(t.name()).declaration.variants()) if (v.name().equals(s.name()) && v.payload() == null) return t; }
        if (t.base() == Base.STRUCT && structs.containsKey(t.name())) { FieldInfo f = structs.get(t.name()).fields.get(s.name()); if (f != null && f.declaration.isStatic()) { if (!s.type().name().equals("Self")) fail("C246", "static field '" + s.name() + "' of '" + t.name() + "' is only accessible as Self." + s.name() + " inside " + t.name(), s.span()); return f.type; } }
        if (s.name().equals("MAX_VALUE") || s.name().equals("MIN_VALUE") || s.name().equals("NaN") || s.name().equals("POSITIVE_INFINITY") || s.name().equals("NEGATIVE_INFINITY")) return t;
        return named(Base.OBJECT, "Object");
    }
    private Type memberType(MemberExpr m) {
        Type t = checkExpr(m.object(), null); if (t.base() == Base.STRUCT && structs.containsKey(t.name())) { FieldInfo f = structs.get(t.name()).fields.get(m.name()); if (f != null) { if (!owner.equals(t.name())) fail("C199", "field '" + m.name() + "' is private", m.span()); model.memberFields.put(m, f); return f.type; } } if (t.base() == Base.OBJECT && m.name().equals("toString")) return named(Base.STRING, "String"); fail("C179", "unknown member '" + m.name() + "'", m.span()); return named(Base.OBJECT, "Object");
    }
    private Type nameType(NameExpr n) { if (n.name().equals("self")) { if (staticContext) fail("C180", "self is not available in a static method", n.span()); return named(structs.containsKey(owner) ? Base.STRUCT : Base.TRAIT, owner); } for (int i = scopes.size() - 1; i >= 0; i--) { Map<String, Type> scope = scopes.get(i); if (scope.containsKey(n.name())) { Type result = narrowedType(n.name(), scope.get(n.name())); if (!isAssigned(n.name())) fail("C239", "variable '" + n.name() + "' may not have been initialized", n.span()); return result; } } if (structs.containsKey(owner)) { FieldInfo f = structs.get(owner).fields.get(n.name()); if (f != null) { model.fieldNames.put(n, f); return f.type; } } fail("C181", "unknown variable '" + n.name() + "'", n.span()); return named(Base.OBJECT, "Object"); }
    private Type lookupNameType(NameExpr n) { if (n.name().equals("self")) return named(structs.containsKey(owner) ? Base.STRUCT : Base.TRAIT, owner); for (int i = scopes.size() - 1; i >= 0; i--) { Map<String, Type> scope = scopes.get(i); if (scope.containsKey(n.name())) return scope.get(n.name()); } if (structs.containsKey(owner)) { FieldInfo f = structs.get(owner).fields.get(n.name()); if (f != null) { model.fieldNames.put(n, f); return f.type; } } fail("C181", "unknown variable '" + n.name() + "'", n.span()); return named(Base.OBJECT, "Object"); }
    private Type lvalueType(Expr e) { Type t = e instanceof NameExpr n ? lookupNameType(n) : e instanceof MemberExpr m ? memberType(m) : e instanceof StaticExpr s ? staticValueType(s) : named(Base.UNKNOWN, "Unknown"); if (e instanceof NameExpr n && isVisible(n.name())) { if (!isMutable(n.name()) && isAssigned(n.name())) fail("C182", "cannot assign to immutable variable '" + n.name() + "'", e.span()); markAssigned(n.name()); } else if (e instanceof NameExpr n && !isMutable(n.name()) && !(structs.containsKey(owner) && structs.get(owner).fields.containsKey(n.name()) && structs.get(owner).fields.get(n.name()).declaration.isStatic() && structs.get(owner).fields.get(n.name()).declaration.mutable())) fail("C182", "cannot assign to immutable variable '" + n.name() + "'", e.span()); if (e instanceof MemberExpr m && model.memberFields.get(m) != null && !model.memberFields.get(m).declaration.mutable()) fail("C183", "field is immutable", e.span()); return t; }

    private Type literalType(Literal l) {
        return switch (l.kind()) {
            case BOOLEAN -> named(Base.BOOLEAN, "Boolean"); case STRING -> named(Base.STRING, "String"); case CHAR -> named(Base.CHAR, "Char"); case NULL -> new Type(Base.NULL, "null", List.of(), true);
            case INT -> { try { BigInteger x = new BigInteger(cleanInteger(l.text())); yield x.bitLength() <= 31 ? named(Base.INTEGER, "Integer") : x.bitLength() <= 63 ? named(Base.LONG, "Long") : named(Base.BIG_INTEGER, "BigInteger"); } catch (NumberFormatException e) { yield named(Base.BIG_INTEGER, "BigInteger"); } }
            case REAL -> { String x = l.text(); String u = x.toUpperCase(); if (u.endsWith("BD")) yield named(Base.BIG_DECIMAL, "BigDecimal"); if (u.endsWith("F")) yield named(Base.FLOAT, "Float"); yield named(Base.DOUBLE, "Double"); }
        };
    }
    public static String cleanInteger(String text) { String s = text.replace("_", ""); boolean neg = s.startsWith("-"); if (neg) s = s.substring(1); int radix = 10; if (s.startsWith("0x") || s.startsWith("0X")) { radix = 16; s = s.substring(2); } else if (s.startsWith("0o") || s.startsWith("0O")) { radix = 8; s = s.substring(2); } else if (s.startsWith("0b") || s.startsWith("0B")) { radix = 2; s = s.substring(2); } BigInteger v = new BigInteger(s, radix); return (neg ? "-" : "") + v; }
    public static BigInteger constantInteger(Expr e) {
        if (e instanceof Literal l && l.kind() == LiteralKind.INT) try { return new BigInteger(cleanInteger(l.text())); } catch (RuntimeException x) { return null; }
        if (e instanceof UnaryExpr u && u.op() == UnaryOp.NEG) {
            BigInteger value = constantInteger(u.operand());
            return value == null ? null : value.negate();
        }
        return null;
    }
    private BigInteger literalInteger(Expr e) { return model.constantInteger(e); }
    private boolean assignable(Type source, Type target, Expr expression) { if (source.base() == Base.TYPE_VAR || target.base() == Base.TYPE_VAR) return true; if (source.base() == Base.STRUCT && target.base() == Base.RUNNABLE) return implementsRunnable(source); if (source.base() == target.base() && (source.args().isEmpty() || target.args().isEmpty())) return !source.nullable() || target.nullable(); if (sameIgnoringNull(source, target)) return !source.nullable() || target.nullable(); if (source.base() == Base.NULL) return target.nullable(); if (target.base() == Base.OBJECT) return true; BigInteger literal = literalInteger(expression); if (literal != null && target.integral()) { try { long v = literal.longValueExact(); switch (target.base()) { case BYTE -> { if (v >= -128 && v <= 127) return true; } case SHORT -> { if (v >= -32768 && v <= 32767) return true; } case INTEGER -> { if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return true; } case LONG -> { return true; } default -> {} } } catch (ArithmeticException ignored) {} } if (source.numeric() && target.numeric() && source.base() != Base.BIG_INTEGER && source.base() != Base.BIG_DECIMAL && target.base() != Base.BIG_INTEGER && target.base() != Base.BIG_DECIMAL) return numericWidening(source.base(), target.base()) && (!source.nullable() || target.nullable()); if (source.base() == Base.STRUCT && target.base() == Base.TRAIT) return conformsToTrait(source, target); if (source.base() == Base.TRAIT && target.base() == Base.TRAIT) return traitConforms(source, target, new HashSet<>()); return false; }
    private boolean implementsRunnable(Type source) { StructInfo info = structs.get(source.name()); return info != null && info.declaration.implementsTypes().stream().anyMatch(x -> x.name().equals("Runnable")); }
    private boolean implementsTrait(String struct, String iface) { return implementsTrait(named(Base.STRUCT, struct), named(Base.TRAIT, iface)); }
    private boolean implementsTrait(Type source, Type target) { return conformsToTrait(source, target); }
    private boolean traitExtends(String child, String ancestor) { TraitInfo i = traits.get(child); if (i == null) return false; return i.declaration.extendsTypes().stream().anyMatch(t -> t.name().equals(ancestor) || traitExtends(t.name(), ancestor)); }
    private void checkAssignable(Type s, Type t, Expr e, String what) { if (!assignable(s, t, e)) fail("C188", what + " of type " + s + " is not assignable to " + t, e.span()); }
    private Type commonTypes(List<Expr> expressions) { Type r = null; for (Expr e : expressions) r = r == null ? model.type(e) : common(r, model.type(e)); return r == null ? named(Base.OBJECT, "Object") : r; }
    private Type common(Type a, Type b) { if (sameIgnoringNull(a, b)) return a; Type p = a.numeric() && b.numeric() ? binaryPromotion(a, b) : null; return p == null ? named(Base.OBJECT, "Object") : p; }
    private Type resolve(TypeRef ref, String current) {
        if (ref == null) return named(Base.VOID, "Void");
        String n = ref.name().equals("Self") ? current : ref.name();
        if (typeVariables.containsKey(n)) {
            if (!ref.args().isEmpty() && reportedGenericArity.add(ref.span())) fail("C102", "generic type '" + n + "' expects 0 type arguments, found " + ref.args().size(), ref.span());
            return new Type(Base.TYPE_VAR, n, List.of(), ref.nullable());
        }
        List<Type> args = ref.args().stream().map(x -> resolve(x, current)).toList();
        int expectedArity = genericArity(n);
        if (ref.explicitArguments() && expectedArity >= 0 && args.size() != expectedArity && reportedGenericArity.add(ref.span())) {
            fail("C102", "generic type '" + n + "' expects " + expectedArity + " type arguments, found " + args.size(), ref.span());
        }
        for (Type argument : args) if (argument.nullable() && reportedNullableTypeArguments.add(ref.span())) {
            fail("C103", "nullable types cannot be used as type arguments", ref.span());
            break;
        }
        Type t = fromName(n, args, ref.nullable());
        if (t.base() == Base.UNKNOWN) {
            if (structs.containsKey(n)) t = new Type(Base.STRUCT, n, args, ref.nullable());
            else if (traits.containsKey(n)) t = new Type(Base.TRAIT, n, args, ref.nullable());
            else if (enums.containsKey(n)) t = new Type(Base.ENUM, n, args, ref.nullable());
            else if (BUILTIN_NAMESPACES.contains(n)) { /* static namespace, not a Solvik value */ }
            else fail("C189", "unknown type '" + n + "'", ref.span());
        }
        return t;
    }
    private int genericArity(String name) {
        return switch (name) {
            case "List", "Stack", "Set" -> 1;
            case "Map" -> 2;
            default -> {
                if (structs.containsKey(name)) yield structs.get(name).declaration.typeParams().size();
                if (traits.containsKey(name)) yield traits.get(name).declaration.typeParams().size();
                if (enums.containsKey(name)) yield enums.get(name).declaration.typeParams().size();
                yield -1;
            }
        };
    }
    private void push() { scopes.add(new HashMap<>()); mutableScopes.add(new HashMap<>()); assignedScopes.add(new HashSet<>()); narrowingScopes.add(new HashMap<>()); }
    private void pop() { int last = scopes.size() - 1; scopes.remove(last); mutableScopes.remove(last); assignedScopes.remove(last); narrowingScopes.remove(last); }
    private Type narrowedType(String name, Type original) { for (int i = narrowingScopes.size() - 1; i >= 0; i--) { Map<String, Type> scope = narrowingScopes.get(i); if (scope.containsKey(name)) return scope.get(name); } return original; }
    private void declare(String name, Type type) { declare(name, type, false, true); }
    private void declare(String name, Type type, boolean mutable) { declare(name, type, mutable, true); }
    private void declare(String name, Type type, boolean mutable, boolean initialized) {
        for (int i = scopes.size() - 1; i >= 0; i--) if (scopes.get(i).containsKey(name)) { fail("C240", "name '" + name + "' is already declared in this scope", model.unit.span()); return; }
        scopes.get(scopes.size() - 1).put(name, type);
        mutableScopes.get(mutableScopes.size() - 1).put(name, mutable);
        if (initialized) markAssignedIn(assignedScopes.get(assignedScopes.size() - 1), name);
    }
    private boolean isVisible(String name) { for (int i = scopes.size() - 1; i >= 0; i--) if (scopes.get(i).containsKey(name)) return true; return false; }
    private boolean isAssigned(String name) { for (int i = assignedScopes.size() - 1; i >= 0; i--) if (assignedScopes.get(i).contains(name)) return true; return false; }
    private void markAssigned(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) { markAssignedIn(assignedScopes.get(i), name); return; }
        }
    }
    private boolean isMutable(String name) { for (int i = mutableScopes.size() - 1; i >= 0; i--) { Map<String, Boolean> scope = mutableScopes.get(i); if (scope.containsKey(name)) return scope.get(name); } return false; }

    private String builtinStatic(Type type, String name) { return switch (type.name()) { case "System", "Math", "Base64", "Hash", "Json", "Time", "Random", "Type", "File", "Test", "Regex", "Thread", "Mutex", "Semaphore", "Process", "Exception", "List", "Map", "Stack", "Set", "Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger", "BigDecimal", "String", "Boolean", "Char" -> type.name() + "." + name; default -> null; }; }
    private String builtinInstance(Type t, String name) { return switch (t.base()) { case STRING -> "String." + name; case LIST -> "List." + name; case MAP -> "Map." + name; case STACK -> "Stack." + name; case SET -> "Set." + name; case WRITER -> "Writer." + name; case READER -> "Reader." + name; case THREAD -> "Thread." + name; case MUTEX -> "Mutex." + name; case SEMAPHORE -> "Semaphore." + name; case PROCESS -> "Process." + name; case REGEX -> "Regex." + name; case EXCEPTION -> "Exception." + name; default -> null; }; }
    private Type builtinReturn(Type type, String name, List<Arg> args) {
        String n = type.name();
        if (name.equals("new") && n.equals("Exception")) return named(Base.EXCEPTION, "Exception");
        if (name.equals("new") && n.equals("Regex")) return generic(Base.REGEX, "Regex", List.of());
        if (name.equals("new") && n.equals("Thread")) return generic(Base.THREAD, "Thread", List.of());
        if (name.equals("new") && n.equals("Mutex")) return generic(Base.MUTEX, "Mutex", List.of());
        if (name.equals("new") && n.equals("Semaphore")) return generic(Base.SEMAPHORE, "Semaphore", List.of());
        if (name.equals("new") && n.equals("Process")) return generic(Base.PROCESS, "Process", List.of());
        if ((type.base() == Base.LIST || type.base() == Base.MAP || type.base() == Base.STACK || type.base() == Base.SET) && (name.equals("new") || name.equals("withCapacity"))) return type;
        if (type.base() == Base.STRING) return switch (name) { case "length", "indexOf" -> named(Base.LONG, "Long"); case "charAt" -> named(Base.CHAR, "Char"); case "contains", "startsWith", "endsWith" -> named(Base.BOOLEAN, "Boolean"); case "split" -> generic(Base.LIST, "List", List.of(named(Base.STRING, "String"))); default -> named(Base.STRING, "String"); };
        if (type.base() == Base.LIST) { Type e = type.args().isEmpty() ? named(Base.OBJECT, "Object") : type.args().get(0); return switch (name) { case "size", "indexOf" -> named(Base.INTEGER, "Integer"); case "isEmpty", "contains", "removeValue" -> named(Base.BOOLEAN, "Boolean"); case "get", "set", "remove" -> e; case "add", "addAll" -> named(Base.BOOLEAN, "Boolean"); case "addAt", "reverse", "sort", "clear" -> named(Base.VOID, "Void"); case "join" -> named(Base.STRING, "String"); case "reversed" -> type; default -> named(Base.OBJECT, "Object"); }; }
        if (type.base() == Base.MAP) { Type k = type.args().size()>0?type.args().get(0):named(Base.OBJECT,"Object"); Type v=type.args().size()>1?type.args().get(1):named(Base.OBJECT,"Object"); return switch(name) { case "size" -> named(Base.INTEGER,"Integer"); case "isEmpty", "containsKey", "containsValue", "removeMapping" -> named(Base.BOOLEAN,"Boolean"); case "get", "put", "remove", "putIfAbsent", "replace" -> new Type(v.base(),v.name(),v.args(),true); case "getOrDefault" -> v; case "keys" -> generic(Base.LIST,"List",List.of(k)); case "values" -> generic(Base.LIST,"List",List.of(v)); case "putAll", "clear" -> named(Base.VOID,"Void"); default -> named(Base.OBJECT,"Object"); }; }
        if (type.base() == Base.STACK) { Type e=type.args().isEmpty()?named(Base.OBJECT,"Object"):type.args().get(0); return switch(name) { case "size" -> named(Base.INTEGER,"Integer"); case "isEmpty" -> named(Base.BOOLEAN,"Boolean"); case "push", "addFirst", "addLast" -> named(Base.VOID,"Void"); case "pop", "poll", "peek", "peekFirst", "peekLast", "removeFirst", "removeLast" -> new Type(e.base(),e.name(),e.args(),true); default -> named(Base.OBJECT,"Object"); }; }
        if (type.base() == Base.SET) { Type e=type.args().isEmpty()?named(Base.OBJECT,"Object"):type.args().get(0); return switch(name) { case "size" -> named(Base.INTEGER,"Integer"); case "isEmpty", "add", "remove", "contains", "addAll", "containsAll" -> named(Base.BOOLEAN,"Boolean"); case "clear" -> named(Base.VOID,"Void"); case "toList" -> generic(Base.LIST,"List",List.of(e)); default -> named(Base.OBJECT,"Object"); }; }
        if (type.base() == Base.WRITER) return named(Base.VOID,"Void");
        if (type.base() == Base.READER) return name.equals("readln") ? new Type(Base.STRING,"String",List.of(),true) : named(Base.STRING,"String");
        if (type.base() == Base.THREAD || type.base() == Base.MUTEX || type.base() == Base.SEMAPHORE) return named(Base.VOID,"Void");
        if (type.base() == Base.PROCESS) return switch (name) { case "stdout", "stderr" -> named(Base.READER,"Reader"); case "stdin" -> named(Base.WRITER,"Writer"); case "exitCode" -> named(Base.LONG,"Long"); default -> named(Base.VOID,"Void"); };
        if (type.base() == Base.REGEX) return name.equals("matches") ? named(Base.BOOLEAN,"Boolean") : name.equals("find") ? new Type(Base.STRING,"String",List.of(),true) : name.equals("all") ? generic(Base.LIST,"List",List.of(named(Base.STRING,"String"))) : named(Base.STRING,"String");
        if (n.equals("System")) return switch (name) { case "getOut", "getErr" -> named(Base.WRITER, "Writer"); case "getIn" -> named(Base.READER, "Reader"); case "getLineSeparator" -> named(Base.STRING, "String"); case "getCurrentTimeMillis", "getNanoTime" -> named(Base.LONG, "Long"); case "getEnv" -> args.isEmpty() ? generic(Base.MAP, "Map", List.of(named(Base.STRING, "String"), named(Base.STRING, "String"))) : new Type(Base.STRING, "String", List.of(), true); case "getProperty" -> args.size() > 1 ? named(Base.STRING, "String") : new Type(Base.STRING, "String", List.of(), true); case "setProperty", "clearProperty" -> new Type(Base.STRING, "String", List.of(), true); default -> named(Base.VOID, "Void"); };
        if (n.equals("Math")) return name.equals("round") ? named(Base.LONG, "Long") : name.equals("sqrt") || name.equals("pow") || name.equals("abs") || name.equals("min") || name.equals("max") || name.equals("floor") || name.equals("ceil") ? named(Base.DOUBLE, "Double") : named(Base.VOID, "Void");
        if (n.equals("Base64") || n.equals("Hash") || n.equals("Json")) return named(Base.STRING, "String");
        if (n.equals("Time")) return name.equals("now") ? named(Base.LONG, "Long") : named(Base.VOID, "Void");
        if (n.equals("Random")) { if (!RANDOM_METHODS.contains(name)) { fail("C247", "unknown Random method '" + name + "'", Span.synthetic(model.unit.span().file())); return named(Base.OBJECT, "Object"); } return name.equals("nextDouble") ? named(Base.DOUBLE, "Double") : named(Base.LONG, "Long"); }
        if (n.equals("Type")) return name.equals("of") ? named(Base.STRING, "String") : named(Base.BOOLEAN, "Boolean");
        if (n.equals("File")) return switch (name) { case "read", "listDir" -> name.equals("read") ? named(Base.STRING, "String") : generic(Base.LIST, "List", List.of(named(Base.STRING, "String"))); case "exists", "delete" -> named(Base.BOOLEAN, "Boolean"); default -> named(Base.VOID, "Void"); };
        if (n.equals("Test")) return named(Base.VOID, "Void");
        if (n.equals("Regex")) return generic(Base.REGEX, "Regex", List.of());
        if (n.equals("Thread")) return generic(Base.THREAD, "Thread", List.of());
        if (n.equals("Mutex")) return generic(Base.MUTEX, "Mutex", List.of());
        if (n.equals("Semaphore")) return generic(Base.SEMAPHORE, "Semaphore", List.of());
        if (n.equals("Process")) return generic(Base.PROCESS, "Process", List.of());
        if (Set.of("List", "Map", "Stack", "Set").contains(n) && (name.equals("new") || name.equals("withCapacity"))) return type;
        if (List.of("Byte", "Short", "Integer", "Long", "Float", "Double", "BigInteger", "BigDecimal", "String", "Boolean", "Char").contains(n) && name.equals("from")) return fromName(n, List.of(), false);
        return named(Base.OBJECT, "Object");
    }

    private void checkBuiltinArguments(Type receiver, String name, List<Arg> args) {
        for (int i = 0; i < args.size(); i++) {
            Type expected = builtinArgumentType(receiver, name, i); Type actual = checkExpr(args.get(i).expression(), expected);
            if (expected != null) checkAssignable(actual, expected, args.get(i).expression(), "argument");
        }
    }

    private Type builtinArgumentType(Type receiver, String name, int index) {
        if (receiver.base() == Base.STRING) return (name.equals("substring") || name.equals("charAt")) ? named(Base.LONG, "Long") : named(Base.STRING, "String");
        if (receiver.base() == Base.LIST) { Type element = receiver.args().isEmpty() ? named(Base.OBJECT, "Object") : receiver.args().get(0); if (name.equals("addAt") || name.equals("set")) return index == 0 ? named(Base.INTEGER, "Integer") : element; if (name.equals("get") || name.equals("remove")) return named(Base.INTEGER, "Integer"); if (name.equals("add") || name.equals("contains") || name.equals("removeValue")) return element; if (name.equals("addAll")) return generic(Base.LIST, "List", List.of(element)); }
        if (receiver.base() == Base.MAP) { Type key = receiver.args().isEmpty() ? named(Base.OBJECT, "Object") : receiver.args().get(0); Type value = receiver.args().size() < 2 ? named(Base.OBJECT, "Object") : receiver.args().get(1); if (name.equals("putAll")) return receiver; if (name.equals("containsValue")) return value; if (name.equals("removeMapping")) return index == 0 ? key : value; if (name.equals("getOrDefault")) return index == 0 ? key : value; if (index == 0) return key; return value; }
        if (receiver.base() == Base.SET) { Type element = receiver.args().isEmpty() ? named(Base.OBJECT, "Object") : receiver.args().get(0); if (name.equals("addAll") || name.equals("containsAll")) return generic(Base.SET, "Set", List.of(element)); return element; }
        if (receiver.base() == Base.STACK) { Type element = receiver.args().isEmpty() ? named(Base.OBJECT, "Object") : receiver.args().get(0); return element; }
        return null;
    }

    private void fail(String code, String message, Span span) { errors.add(new Diagnostic(code, message, span)); }
}
