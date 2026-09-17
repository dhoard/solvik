/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.lowering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import org.solvik.ast.AstNode;
import org.solvik.ast.declaration.DelegateDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.CharLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.NullLiteralNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.expression.SuperExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.expression.TypeTestExprNode;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.ast.expression.UnaryOperator;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.BreakStmtNode;
import org.solvik.ast.statement.ContinueStmtNode;
import org.solvik.ast.statement.ElseBranchNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.ForStmtNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.ast.statement.WhileStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.InterfaceSymbol;
import org.solvik.semantic.PropertySymbol;
import org.solvik.semantic.ResolvedMethod;
import org.solvik.semantic.Symbol;
import org.solvik.semantic.VariableSymbol;
import org.solvik.source.SourceSpan;
import org.solvik.source.StringEscapes;
import org.solvik.truffle.SolvikEvalRootNode;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.SolvikLanguage;
import org.solvik.truffle.SolvikRootNode;
import org.solvik.truffle.nodes.SolvikAddNodeGen;
import org.solvik.truffle.nodes.SolvikBlockNode;
import org.solvik.truffle.nodes.SolvikBoolLiteralNode;
import org.solvik.truffle.nodes.SolvikBreakNode;
import org.solvik.truffle.nodes.SolvikCastNode;
import org.solvik.truffle.nodes.SolvikCharLiteralNode;
import org.solvik.truffle.nodes.SolvikCoalesceNode;
import org.solvik.truffle.nodes.SolvikContinueNode;
import org.solvik.truffle.nodes.SolvikConvertNode;
import org.solvik.truffle.nodes.SolvikDivNodeGen;
import org.solvik.truffle.nodes.SolvikEqualNodeGen;
import org.solvik.truffle.nodes.SolvikExpressionNode;
import org.solvik.truffle.nodes.SolvikFloatingLiteralNode;
import org.solvik.truffle.nodes.SolvikForNode;
import org.solvik.truffle.nodes.SolvikGreaterOrEqualNodeGen;
import org.solvik.truffle.nodes.SolvikGreaterThanNodeGen;
import org.solvik.truffle.nodes.SolvikIfNode;
import org.solvik.truffle.nodes.SolvikIntLiteralNode;
import org.solvik.truffle.nodes.SolvikInvokeMethodNode;
import org.solvik.truffle.nodes.SolvikInvokeNode;
import org.solvik.truffle.nodes.SolvikLessOrEqualNodeGen;
import org.solvik.truffle.nodes.SolvikLessThanNodeGen;
import org.solvik.truffle.nodes.SolvikLogicalAndNode;
import org.solvik.truffle.nodes.SolvikLogicalNotNodeGen;
import org.solvik.truffle.nodes.SolvikLogicalOrNode;
import org.solvik.truffle.nodes.SolvikLongLiteralNode;
import org.solvik.truffle.nodes.SolvikMulNodeGen;
import org.solvik.truffle.nodes.SolvikNegateNodeGen;
import org.solvik.truffle.nodes.SolvikNewNode;
import org.solvik.truffle.nodes.SolvikNumericBinaryNode;
import org.solvik.truffle.nodes.SolvikNumericComparisonNode;
import org.solvik.truffle.nodes.SolvikNumericNegateNode;
import org.solvik.truffle.nodes.SolvikNullLiteralNode;
import org.solvik.truffle.nodes.SolvikPrintNode;
import org.solvik.truffle.nodes.SolvikPrintlnNode;
import org.solvik.truffle.nodes.SolvikReadLocalVariableNodeGen;
import org.solvik.truffle.nodes.SolvikReadPropertyNode;
import org.solvik.truffle.nodes.SolvikReturnNode;
import org.solvik.truffle.nodes.SolvikStatementNode;
import org.solvik.truffle.nodes.SolvikStringLiteralNode;
import org.solvik.truffle.nodes.SolvikSubNodeGen;
import org.solvik.truffle.nodes.SolvikSuperConstructorNode;
import org.solvik.truffle.nodes.SolvikTypeTestNode;
import org.solvik.truffle.nodes.SolvikWhileNode;
import org.solvik.truffle.nodes.SolvikWriteLocalVariableNodeGen;
import org.solvik.truffle.nodes.SolvikWritePropertyNode;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.IntType;
import org.solvik.type.LongType;
import org.solvik.type.NumericTypes;
import org.solvik.type.ShortType;
import org.solvik.type.Type;
import org.solvik.type.UnitType;

/**
 * Lowers a statically checked program to the Truffle AST backend (docs/ARCHITECTURE.md "AST First,
 * Bytecode Later"). The lowering consumes only the {@link CheckedProgram} compiler facts; it never
 * re-parses or re-checks. Because a program with any error diagnostic produces no checked program,
 * lowering can assume every expression has a recorded type, every name resolves, and every member
 * access and call has a static target.
 *
 * <p>Frame slots are typed from the statically analysed binding types, so {@code Int} and
 * {@code Boolean} locals and parameters use primitive frame storage. A method or constructor has an
 * implicit {@code this} receiver in frame slot zero; a constructor also runs declaration
 * initializers before the explicit {@code init} body.
 */
public final class SolvikLowering {

    private final CheckedProgram program;
    private final Source source;
    private final SolvikLanguage language;
    private final Map<String, SolvikFunction> runtimeFunctions = new LinkedHashMap<>();
    private final Map<FunctionDeclNode, SolvikFunction> byDeclaration = new IdentityHashMap<>();
    /** Runtime handles of compiler-synthesized delegation forwarding methods, keyed by symbol. */
    private final Map<FunctionSymbol, SolvikFunction> bySynthesizedSymbol = new IdentityHashMap<>();
    /** Synthesized forwarding methods already lowered, so an inherited one is compiled only once. */
    private final Set<FunctionSymbol> loweredSynthesized = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ClassSymbol, SolvikClass> runtimeClasses = new IdentityHashMap<>();
    private final Map<PropertySymbol, SolvikClass> propertyOwners = new IdentityHashMap<>();
    private final Map<VariableSymbol, Integer> slots = new IdentityHashMap<>();
    private FrameDescriptor.Builder frameBuilder;
    private int thisSlot = -1;

    private SolvikLowering(CheckedProgram program, Source source, SolvikLanguage language) {
        this.program = program;
        this.source = source;
        this.language = language;
    }

    /** Lowers a checked program, returning its runtime representation. */
    public static LoweredProgram lower(CheckedProgram program, Source source, SolvikLanguage language) {
        return new SolvikLowering(program, source, language).run();
    }

    private LoweredProgram run() {
        for (FunctionSymbol function : program.functions().values()) {
            if (function.isBuiltin()) {
                continue;
            }
            SolvikFunction runtime = new SolvikFunction(function.name());
            runtimeFunctions.put(function.name(), runtime);
            byDeclaration.put(function.declaration(), runtime);
        }
        for (InterfaceSymbol interfaceSymbol : program.interfaces().values()) {
            for (FunctionSymbol member : interfaceSymbol.declaredMembers()) {
                if (member.hasImplementation()) {
                    // One runtime handle per default method; every conforming class's table entry
                    // points at this same handle, so a default is compiled once for all implementors.
                    byDeclaration.put(member.declaration(), new SolvikFunction(interfaceSymbol.name() + "." + member.name()));
                }
            }
        }
        for (ClassSymbol classSymbol : program.classes().values()) {
            SolvikClass runtimeClass = createRuntimeClass(classSymbol);
            runtimeClasses.put(classSymbol, runtimeClass);
            for (PropertySymbol property : classSymbol.properties()) {
                propertyOwners.put(property, runtimeClass);
            }
            for (FunctionSymbol method : classSymbol.declaredMethods()) {
                SolvikFunction runtime = new SolvikFunction(classSymbol.name() + "." + method.name());
                byDeclaration.put(method.declaration(), runtime);
            }
            for (FunctionSymbol method : classSymbol.methods()) {
                if (method.isSynthesized() && !bySynthesizedSymbol.containsKey(method)) {
                    // One forwarding body per resolved delegation, so a subclass that inherits the
                    // superclass's forwarding implementation reuses the same runtime handle.
                    bySynthesizedSymbol.put(method, new SolvikFunction(classSymbol.name() + "." + method.name() + "(delegate)"));
                }
            }
            runtimeClass.installConstructor(new SolvikFunction(classSymbol.name() + ".<init>"));
        }
        // Link the runtime class hierarchy and interface set once every runtime class exists, so a
        // runtime type test can walk the superclass chain and the transitive interface closure.
        for (ClassSymbol classSymbol : program.classes().values()) {
            SolvikClass runtimeClass = runtimeClasses.get(classSymbol);
            classSymbol.superClass().ifPresent(superSymbol -> runtimeClass.setSuperClass(runtimeClasses.get(superSymbol)));
            for (InterfaceSymbol face : classSymbol.allInterfaces()) {
                collectInterfaceNames(face, runtimeClass, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
        // Fill each class's virtual method table with inherited methods plus its own overrides,
        // reusing the declaring class's runtime handle so overriding does not duplicate bodies.
        for (ClassSymbol classSymbol : program.classes().values()) {
            SolvikClass runtimeClass = runtimeClasses.get(classSymbol);
            for (FunctionSymbol method : classSymbol.methods()) {
                runtimeClass.installMethod(method.name(), runtimeHandle(method));
            }
        }
        for (FunctionSymbol function : program.functions().values()) {
            if (!function.isBuiltin()) {
                lowerCallable(function, false);
            }
        }
        // Default method bodies lower once, before class tables reference them.
        for (InterfaceSymbol interfaceSymbol : program.interfaces().values()) {
            for (FunctionSymbol member : interfaceSymbol.declaredMembers()) {
                if (member.hasImplementation()) {
                    lowerCallable(member, true);
                }
            }
        }
        for (ClassSymbol classSymbol : program.classes().values()) {
            for (FunctionSymbol method : classSymbol.declaredMethods()) {
                lowerCallable(method, true);
            }
            lowerConstructor(classSymbol);
        }
        // Lower each synthesized delegation forwarding method exactly once, after every runtime class
        // and property owner is known so the delegate property key resolves.
        for (ClassSymbol classSymbol : program.classes().values()) {
            for (FunctionSymbol method : classSymbol.methods()) {
                if (method.isSynthesized() && loweredSynthesized.add(method)) {
                    lowerSynthesizedMethod(method);
                }
            }
        }
        SolvikFunction entryPoint = program.entryPoint().map(ignored -> runtimeFunctions.get("main")).orElse(null);
        CallTarget evalTarget = new SolvikEvalRootNode(language, entryPoint).getCallTarget();
        return new LoweredProgram(program, runtimeFunctions, evalTarget);
    }

    private SolvikClass createRuntimeClass(ClassSymbol classSymbol) {
        List<String> names = new ArrayList<>();
        List<Boolean> mutable = new ArrayList<>();
        for (PropertySymbol property : classSymbol.properties()) {
            names.add(property.name());
            mutable.add(property.isMutable());
        }
        return new SolvikClass(classSymbol.name(), names, mutable);
    }

    /**
     * Records every interface reachable from {@code face}, including the ones it extends, so a
     * runtime {@code is} test against an extended interface succeeds for a conforming class.
     */
    private static void collectInterfaceNames(InterfaceSymbol face, SolvikClass runtimeClass, Set<InterfaceSymbol> visited) {
        if (!visited.add(face)) {
            return;
        }
        runtimeClass.addInterfaceName(face.name());
        for (InterfaceSymbol parent : face.superInterfaces()) {
            collectInterfaceNames(parent, runtimeClass, visited);
        }
    }

    /**
     * The runtime handle of a virtual-table entry: the written declaration's body, or the synthesized
     * forwarding body for a method the compiler created to satisfy an interface member by delegation.
     */
    private SolvikFunction runtimeHandle(FunctionSymbol method) {
        if (method.isSynthesized()) {
            SolvikFunction synthesized = bySynthesizedSymbol.get(method);
            if (synthesized == null) {
                throw new IllegalStateException("no runtime handle for synthesized method '" + method.name() + "'");
            }
            return synthesized;
        }
        SolvikFunction runtime = byDeclaration.get(method.declaration());
        if (runtime == null) {
            throw new IllegalStateException("no runtime handle for method '" + method.name() + "'");
        }
        return runtime;
    }

    // ---------------------------------------------------------------------------------------------
    // Callables
    // ---------------------------------------------------------------------------------------------

    /**
     * Lowers one callable. {@code hasReceiver} is true for a class instance method and for an
     * interface default method, both of which take the receiver in frame slot zero.
     */
    private void lowerCallable(FunctionSymbol function, boolean hasReceiver) {
        slots.clear();
        thisSlot = -1;
        frameBuilder = FrameDescriptor.newBuilder();
        List<Integer> parameterSlots = new ArrayList<>();
        List<FrameSlotKind> parameterKinds = new ArrayList<>();
        if (hasReceiver) {
            int slot = frameBuilder.addSlot(FrameSlotKind.Object, "this", null);
            thisSlot = slot;
            parameterSlots.add(slot);
            parameterKinds.add(FrameSlotKind.Object);
        }
        for (VariableSymbol parameter : function.parameters()) {
            FrameSlotKind kind = kindOf(parameter.type());
            int slot = frameBuilder.addSlot(kind, parameter.name(), null);
            slots.put(parameter, slot);
            parameterSlots.add(slot);
            parameterKinds.add(kind);
        }
        SolvikStatementNode body = lowerBlock(function.declaration().body());
        FrameDescriptor descriptor = frameBuilder.build();
        boolean returnsValue = function.isReturnTypeKnown() && function.returnType() != UnitType.INSTANCE;
        SourceSpan span = function.declaration().span();
        SolvikRootNode root = new SolvikRootNode(language, descriptor, body, function.name(), returnsValue, //
                        toIntArray(parameterSlots), toKindArray(parameterKinds), source, span.startOffset(), span.length());
        byDeclaration.get(function.declaration()).install(root.getCallTarget());
    }

    private void lowerConstructor(ClassSymbol classSymbol) {
        slots.clear();
        thisSlot = -1;
        frameBuilder = FrameDescriptor.newBuilder();
        int receiverSlot = frameBuilder.addSlot(FrameSlotKind.Object, "this", null);
        thisSlot = receiverSlot;
        List<Integer> parameterSlots = new ArrayList<>();
        List<FrameSlotKind> parameterKinds = new ArrayList<>();
        parameterSlots.add(receiverSlot);
        parameterKinds.add(FrameSlotKind.Object);
        FunctionSymbol constructor = classSymbol.constructor().orElse(null);
        if (constructor != null) {
            for (VariableSymbol parameter : constructor.parameters()) {
                FrameSlotKind kind = kindOf(parameter.type());
                int slot = frameBuilder.addSlot(kind, parameter.name(), null);
                slots.put(parameter, slot);
                parameterSlots.add(slot);
                parameterKinds.add(kind);
            }
        }
        SolvikStatementNode body = lowerConstructorBody(classSymbol, constructor);
        FrameDescriptor descriptor = frameBuilder.build();
        SourceSpan span = classSymbol.declaration().span();
        SolvikRootNode root = new SolvikRootNode(language, descriptor, body, classSymbol.name() + ".<init>", false, //
                        toIntArray(parameterSlots), toKindArray(parameterKinds), source, span.startOffset(), span.length());
        runtimeClasses.get(classSymbol).constructor().install(root.getCallTarget());
    }

    /**
     * Lowers a compiler-synthesized delegation forwarding method (docs/LANGUAGE_SPEC.md section 9):
     * {@code return this.<delegate>.<member>(arguments)} for a value-returning member, or the same
     * call as a statement for a {@code Unit} member. The call dispatches by name on the delegate
     * value's runtime class, so the requirement is satisfied by whatever the delegate object
     * implements, including an interface default.
     */
    private void lowerSynthesizedMethod(FunctionSymbol function) {
        slots.clear();
        thisSlot = -1;
        frameBuilder = FrameDescriptor.newBuilder();
        int receiverSlot = frameBuilder.addSlot(FrameSlotKind.Object, "this", null);
        thisSlot = receiverSlot;
        List<Integer> parameterSlots = new ArrayList<>();
        List<FrameSlotKind> parameterKinds = new ArrayList<>();
        parameterSlots.add(receiverSlot);
        parameterKinds.add(FrameSlotKind.Object);
        for (VariableSymbol parameter : function.parameters()) {
            FrameSlotKind kind = kindOf(parameter.type());
            int slot = frameBuilder.addSlot(kind, parameter.name(), null);
            slots.put(parameter, slot);
            parameterSlots.add(slot);
            parameterKinds.add(kind);
        }
        SolvikExpressionNode delegateReceiver = new SolvikReadPropertyNode(SolvikReadLocalVariableNodeGen.create(thisSlot), propertyKey(function.delegateProperty()));
        SolvikExpressionNode[] arguments = new SolvikExpressionNode[function.parameters().size()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = SolvikReadLocalVariableNodeGen.create(slots.get(function.parameters().get(i)));
        }
        SolvikExpressionNode call = new SolvikInvokeMethodNode(function.forwardedDelegate().name(), delegateReceiver, arguments);
        boolean returnsValue = function.isReturnTypeKnown() && function.returnType() != UnitType.INSTANCE;
        SolvikStatementNode body = returnsValue ? new SolvikReturnNode(call) : call;
        SourceSpan span = function.declarationSpan();
        body.setSourceSection(span.startOffset(), span.length());
        FrameDescriptor descriptor = frameBuilder.build();
        SolvikRootNode root = new SolvikRootNode(language, descriptor, body, function.name() + "(delegate)", returnsValue, //
                        toIntArray(parameterSlots), toKindArray(parameterKinds), source, span.startOffset(), span.length());
        bySynthesizedSymbol.get(function).install(root.getCallTarget());
    }

    /**
     * Builds a constructor body: every property and delegate declaration initializer runs first, in
     * source declaration order, then the explicit {@code init} body when present.
     */
    private SolvikStatementNode lowerConstructorBody(ClassSymbol classSymbol, FunctionSymbol constructor) {
        List<SolvikStatementNode> statements = new ArrayList<>();
        CallExprNode superCall = constructor == null ? null : superCallOf(constructor);
        ClassSymbol superClass = classSymbol.superClass().orElse(null);
        if (superClass != null) {
            SolvikFunction superConstructor = runtimeClasses.get(superClass).constructor();
            SolvikExpressionNode[] superArguments = superCall == null ? new SolvikExpressionNode[0] : lowerArguments(superCall.arguments());
            statements.add(new SolvikSuperConstructorNode(superConstructor, thisSlot, superArguments));
        }
        for (AstNode member : classSymbol.declaration().members()) {
            String memberName = null;
            ExpressionNode initializer = null;
            if (member instanceof PropertyDeclNode property) {
                memberName = property.name();
                initializer = property.initializer().orElse(null);
            } else if (member instanceof DelegateDeclNode delegate) {
                memberName = delegate.name();
                initializer = delegate.initializer().orElse(null);
            }
            if (initializer == null) {
                continue;
            }
            PropertySymbol symbol = classSymbol.property(memberName).orElseThrow(() -> new IllegalStateException("no symbol for property"));
            SolvikExpressionNode receiver = SolvikReadLocalVariableNodeGen.create(thisSlot);
            SolvikExpressionNode value = lowerExpression(initializer);
            SolvikWritePropertyNode write = new SolvikWritePropertyNode(receiver, value, propertyKey(symbol));
            statements.add(setSource(write, member));
        }
        if (constructor != null) {
            List<SolvikStatementNode> bodyStatements = new ArrayList<>();
            boolean skipSuper = superCall != null;
            for (StatementNode statement : constructor.initDeclaration().body().statements()) {
                if (skipSuper && statement instanceof ExprStmtNode expressionStatement && expressionStatement.expression() == superCall) {
                    skipSuper = false;
                    continue;
                }
                bodyStatements.add(lowerStatement(statement));
            }
            statements.add(new SolvikBlockNode(bodyStatements.toArray(SolvikStatementNode[]::new)));
        }
        return new SolvikBlockNode(statements.toArray(SolvikStatementNode[]::new));
    }

    /** The sanctioned {@code super(...)} call opening a subclass {@code init} body, or {@code null}. */
    private static CallExprNode superCallOf(FunctionSymbol constructor) {
        List<StatementNode> statements = constructor.initDeclaration().body().statements();
        if (statements.isEmpty()) {
            return null;
        }
        if (statements.get(0) instanceof ExprStmtNode expressionStatement && expressionStatement.expression() instanceof CallExprNode call && call.callee() instanceof SuperExprNode) {
            return call;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Statements
    // ---------------------------------------------------------------------------------------------

    private SolvikBlockNode lowerBlock(BlockNode block) {
        SolvikStatementNode[] statements = new SolvikStatementNode[block.statements().size()];
        for (int i = 0; i < statements.length; i++) {
            statements[i] = lowerStatement(block.statements().get(i));
        }
        SolvikBlockNode node = new SolvikBlockNode(statements);
        setSource(node, block);
        return node;
    }

    private SolvikStatementNode lowerStatement(StatementNode statement) {
        return switch (statement.kind()) {
            case LOCAL_DECL -> lowerLocalDecl((LocalDeclNode) statement);
            case IF_STMT -> lowerIf((IfStmtNode) statement);
            case WHILE_STMT -> lowerWhile((WhileStmtNode) statement);
            case FOR_STMT -> lowerFor((ForStmtNode) statement);
            case BREAK_STMT -> setSource(new SolvikBreakNode(), statement);
            case CONTINUE_STMT -> setSource(new SolvikContinueNode(), statement);
            case ASSIGN_STMT -> lowerAssign((AssignStmtNode) statement);
            case RETURN_STMT -> lowerReturn((ReturnStmtNode) statement);
            case EXPR_STMT -> lowerExpression(((ExprStmtNode) statement).expression());
            default -> throw new IllegalStateException("not a statement kind: " + statement.kind());
        };
    }

    private SolvikStatementNode lowerLocalDecl(LocalDeclNode declaration) {
        VariableSymbol symbol = program.symbolOf(declaration).orElseThrow(() -> new IllegalStateException("no symbol for local declaration"));
        int slot = allocateSlot(symbol);
        SolvikExpressionNode value = lowerExpression(declaration.initializer());
        SolvikStatementNode node = SolvikWriteLocalVariableNodeGen.create(value, slot);
        return setSource(node, declaration);
    }

    private SolvikStatementNode lowerAssign(AssignStmtNode statement) {
        if (statement.target() instanceof NameRefExprNode name) {
            Symbol symbol = program.symbolOf(name).orElseThrow(() -> new IllegalStateException("no symbol for assignment target"));
            if (!(symbol instanceof VariableSymbol variable)) {
                throw new IllegalStateException("assignment target is not a variable");
            }
            SolvikExpressionNode value = lowerExpression(statement.value());
            SolvikStatementNode node = SolvikWriteLocalVariableNodeGen.create(value, allocateSlot(variable));
            return setSource(node, statement);
        }
        if (statement.target() instanceof MemberAccessExprNode member) {
            PropertySymbol property = program.propertyOf(member).orElseThrow(() -> new IllegalStateException("no property for assignment target"));
            SolvikExpressionNode receiver = lowerExpression(member.receiver());
            SolvikExpressionNode value = lowerExpression(statement.value());
            SolvikWritePropertyNode node = new SolvikWritePropertyNode(receiver, value, propertyKey(property));
            return setSource(node, statement);
        }
        throw new IllegalStateException("assignment to an unsupported target reached lowering");
    }

    private SolvikStatementNode lowerReturn(ReturnStmtNode statement) {
        SolvikExpressionNode value = statement.value().map(this::lowerExpression).orElse(null);
        SolvikStatementNode node = new SolvikReturnNode(value);
        return setSource(node, statement);
    }

    private SolvikStatementNode lowerIf(IfStmtNode statement) {
        SolvikExpressionNode condition = lowerExpression(statement.condition());
        SolvikStatementNode thenBlock = lowerBlock(statement.thenBlock());
        SolvikStatementNode elseBranch = statement.elseBranch().map(this::lowerElseBranch).orElse(null);
        SolvikIfNode node = new SolvikIfNode(condition, thenBlock, elseBranch);
        return setSource(node, statement);
    }

    private SolvikStatementNode lowerElseBranch(ElseBranchNode branch) {
        if (branch.isChainedIf()) {
            return lowerStatement(branch.chainedIf().get());
        }
        return lowerBlock(branch.block().get());
    }

    private SolvikStatementNode lowerWhile(WhileStmtNode statement) {
        SolvikExpressionNode condition = lowerExpression(statement.condition());
        SolvikStatementNode body = lowerBlock(statement.body());
        SolvikWhileNode node = new SolvikWhileNode(condition, body);
        return setSource(node, statement);
    }

    private SolvikStatementNode lowerFor(ForStmtNode statement) {
        SolvikStatementNode[] initializer = statement.initializer().map(init -> new SolvikStatementNode[]{lowerStatement(init)}).orElse(new SolvikStatementNode[0]);
        SolvikExpressionNode condition = statement.condition().map(this::lowerExpression).orElse(null);
        SolvikStatementNode update = statement.update().map(this::lowerStatement).orElse(null);
        SolvikStatementNode body = lowerBlock(statement.body());
        SolvikForNode node = new SolvikForNode(initializer, condition, update, body);
        return setSource(node, statement);
    }

    // ---------------------------------------------------------------------------------------------
    // Expressions
    // ---------------------------------------------------------------------------------------------

    private SolvikExpressionNode lowerExpression(ExpressionNode expression) {
        if (expression instanceof ParenExprNode paren) {
            // Parentheses are retained syntactically; lowering reuses the inner node and must not
            // set a second source section on it.
            return lowerExpression(paren.inner());
        }
        SolvikExpressionNode node = switch (expression.kind()) {
            case INT_LITERAL -> new SolvikIntLiteralNode(Integer.parseInt(((IntLiteralNode) expression).lexeme()));
            case LONG_LITERAL -> lowerLongLiteral((LongLiteralNode) expression);
            case FLOATING_LITERAL -> lowerFloatingLiteral((FloatingLiteralNode) expression);
            case CHAR_LITERAL -> new SolvikCharLiteralNode(decodeChar(((CharLiteralNode) expression).lexeme()));
            case BOOL_LITERAL -> new SolvikBoolLiteralNode(((BoolLiteralNode) expression).value());
            case STRING_LITERAL -> new SolvikStringLiteralNode(StringEscapes.unescape(((StringLiteralNode) expression).lexeme()));
            case RAW_STRING_LITERAL -> new SolvikStringLiteralNode(((RawStringLiteralNode) expression).value());
            case NULL_LITERAL -> new SolvikNullLiteralNode();
            case NAME_REF_EXPR -> lowerNameReference((NameRefExprNode) expression);
            case THIS_EXPR -> lowerThis((ThisExprNode) expression);
            case UNARY_EXPR -> lowerUnary((UnaryExprNode) expression);
            case BINARY_EXPR -> lowerBinary((BinaryExprNode) expression);
            case TYPE_TEST_EXPR -> lowerTypeTest((TypeTestExprNode) expression);
            case CAST_EXPR -> lowerCast((CastExprNode) expression);
            case CALL_EXPR -> lowerCall((CallExprNode) expression);
            case MEMBER_ACCESS_EXPR -> lowerMemberRead((MemberAccessExprNode) expression);
            default -> throw new IllegalStateException("not a lowerable expression kind: " + expression.kind());
        };
        return setSource(node, expression);
    }

    private SolvikExpressionNode lowerNameReference(NameRefExprNode name) {
        Symbol symbol = program.symbolOf(name).orElseThrow(() -> new IllegalStateException("no symbol for name '" + name.name() + "'"));
        if (!(symbol instanceof VariableSymbol variable)) {
            throw new IllegalStateException("name '" + name.name() + "' is not a variable");
        }
        return SolvikReadLocalVariableNodeGen.create(allocateSlot(variable));
    }

    private static SolvikExpressionNode lowerLongLiteral(LongLiteralNode literal) {
        String digits = literal.lexeme();
        return new SolvikLongLiteralNode(Long.parseLong(digits.substring(0, digits.length() - 1)));
    }

    private static SolvikExpressionNode lowerFloatingLiteral(FloatingLiteralNode literal) {
        return new SolvikFloatingLiteralNode(literal.isFloat(), Double.parseDouble(literal.numericText()));
    }

    /** Decodes a validated character literal into its single UTF-16 code unit. */
    private static char decodeChar(String lexeme) {
        String content = lexeme.substring(1, lexeme.length() - 1);
        if (content.charAt(0) != '\\') {
            return content.charAt(0);
        }
        return switch (content.charAt(1)) {
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '0' -> '\0';
            default -> throw new IllegalStateException("invalid character literal reached lowering: " + lexeme);
        };
    }

    private SolvikExpressionNode lowerThis(ThisExprNode expression) {
        if (thisSlot < 0) {
            throw new IllegalStateException("'this' reached lowering outside a method or init");
        }
        return SolvikReadLocalVariableNodeGen.create(thisSlot);
    }

    private SolvikExpressionNode lowerMemberRead(MemberAccessExprNode member) {
        PropertySymbol property = program.propertyOf(member).orElseThrow(() -> new IllegalStateException("no property for member read"));
        SolvikExpressionNode receiver = member.receiver() instanceof SuperExprNode ? thisReceiver() : lowerExpression(member.receiver());
        return new SolvikReadPropertyNode(receiver, propertyKey(property), member.isSafe());
    }

    /** Lowers a type test {@code value is T} to a runtime check against the resolved target type. */
    private SolvikExpressionNode lowerTypeTest(TypeTestExprNode expression) {
        Type target = program.testedTypeOf(expression).orElseThrow(() -> new IllegalStateException("no target type for a type test"));
        return new SolvikTypeTestNode(target, runtimeClassOf(target), lowerExpression(expression.operand()));
    }

    /** Lowers a checked cast {@code value as T} to a runtime check that raises on an unsuccessful cast. */
    private SolvikExpressionNode lowerCast(CastExprNode expression) {
        Type target = program.testedTypeOf(expression).orElseThrow(() -> new IllegalStateException("no target type for a cast"));
        return new SolvikCastNode(target, runtimeClassOf(target), lowerExpression(expression.operand()));
    }

    /** The runtime class of a nominal target type, or {@code null} for a built-in or interface target. */
    private SolvikClass runtimeClassOf(Type target) {
        if (!(target instanceof ClassType)) {
            return null;
        }
        ClassSymbol symbol = program.classSymbol(target.name()).orElse(null);
        return symbol == null ? null : runtimeClasses.get(symbol);
    }

    private SolvikExpressionNode lowerUnary(UnaryExprNode expression) {
        SolvikExpressionNode operand = lowerExpression(expression.operand());
        if (expression.operator() == UnaryOperator.NOT) {
            return SolvikLogicalNotNodeGen.create(operand);
        }
        if (program.typeOf(expression.operand()).orElse(null) == IntType.INSTANCE) {
            return SolvikNegateNodeGen.create(operand);
        }
        return new SolvikNumericNegateNode(operand);
    }

    private SolvikExpressionNode lowerBinary(BinaryExprNode expression) {
        SolvikExpressionNode left = lowerExpression(expression.left());
        SolvikExpressionNode right = lowerExpression(expression.right());
        BinaryOperator operator = expression.operator();
        if (operator == BinaryOperator.COALESCE) {
            // Null coalescing short-circuits the right operand; static analysis needs the left nullable.
            return new SolvikCoalesceNode(left, right);
        }
        Type operandType = program.typeOf(expression.left()).orElse(null);
        // Non-Int numeric types use the generic numeric nodes; Int and String keep the specialized
        // Phase 5 nodes.
        if (NumericTypes.isNumeric(operandType) && operandType != IntType.INSTANCE) {
            switch (operator) {
                case ADD, SUB, MUL, DIV -> {
                    return new SolvikNumericBinaryNode(numericBinaryOp(operator), left, right);
                }
                case LT, LE, GT, GE -> {
                    return new SolvikNumericComparisonNode(numericComparisonOp(operator), left, right);
                }
                default -> {
                    // Equality and logical operators fall through to the shared nodes below.
                }
            }
        }
        return switch (operator) {
            case ADD -> SolvikAddNodeGen.create(left, right);
            case SUB -> SolvikSubNodeGen.create(left, right);
            case MUL -> SolvikMulNodeGen.create(left, right);
            case DIV -> SolvikDivNodeGen.create(left, right);
            case LT -> SolvikLessThanNodeGen.create(left, right);
            case LE -> SolvikLessOrEqualNodeGen.create(left, right);
            case GT -> SolvikGreaterThanNodeGen.create(left, right);
            case GE -> SolvikGreaterOrEqualNodeGen.create(left, right);
            case EQ -> SolvikEqualNodeGen.create(left, right);
            case NEQ -> SolvikLogicalNotNodeGen.create(SolvikEqualNodeGen.create(left, right));
            case AND -> new SolvikLogicalAndNode(left, right);
            case OR -> new SolvikLogicalOrNode(left, right);
            case COALESCE -> throw new IllegalStateException("coalesce is lowered before the operator switch");
        };
    }

    private static SolvikNumericBinaryNode.Op numericBinaryOp(BinaryOperator operator) {
        return switch (operator) {
            case ADD -> SolvikNumericBinaryNode.Op.ADD;
            case SUB -> SolvikNumericBinaryNode.Op.SUB;
            case MUL -> SolvikNumericBinaryNode.Op.MUL;
            case DIV -> SolvikNumericBinaryNode.Op.DIV;
            default -> throw new IllegalStateException("not an arithmetic operator: " + operator);
        };
    }

    private static SolvikNumericComparisonNode.Op numericComparisonOp(BinaryOperator operator) {
        return switch (operator) {
            case LT -> SolvikNumericComparisonNode.Op.LT;
            case LE -> SolvikNumericComparisonNode.Op.LE;
            case GT -> SolvikNumericComparisonNode.Op.GT;
            case GE -> SolvikNumericComparisonNode.Op.GE;
            default -> throw new IllegalStateException("not an ordering operator: " + operator);
        };
    }

    private SolvikExpressionNode lowerCall(CallExprNode expression) {
        Optional<Type> conversion = program.conversionOf(expression);
        if (conversion.isPresent()) {
            return lowerConversion(expression, conversion.get());
        }
        Optional<ClassSymbol> constructorClass = program.constructorOf(expression);
        if (constructorClass.isPresent()) {
            return lowerConstruction(expression, constructorClass.get());
        }
        Optional<ResolvedMethod> resolvedMethod = program.methodOf(expression);
        if (resolvedMethod.isPresent()) {
            return lowerMethodCall(expression, resolvedMethod.get());
        }
        if (!(expression.callee() instanceof NameRefExprNode name)) {
            throw new IllegalStateException("unsupported call callee reached lowering");
        }
        Symbol symbol = program.symbolOf(name).orElseThrow(() -> new IllegalStateException("no symbol for callee '" + name.name() + "'"));
        if (!(symbol instanceof FunctionSymbol function)) {
            throw new IllegalStateException("callee '" + name.name() + "' is not a function");
        }
        SolvikExpressionNode[] arguments = lowerArguments(expression.arguments());
        if (function.isBuiltin()) {
            if (arguments.length != 1) {
                throw new IllegalStateException("built-in '" + function.name() + "' expects one argument");
            }
            return switch (function.name()) {
                case "print" -> new SolvikPrintNode(arguments[0]);
                case "println" -> new SolvikPrintlnNode(arguments[0]);
                default -> throw new IllegalStateException("unknown built-in '" + function.name() + "'");
            };
        }
        SolvikFunction runtime = runtimeFunctions.get(function.name());
        if (runtime == null) {
            throw new IllegalStateException("no lowered function for '" + function.name() + "'");
        }
        return new SolvikInvokeNode(runtime, arguments);
    }

    private SolvikExpressionNode lowerConstruction(CallExprNode expression, ClassSymbol classSymbol) {
        SolvikExpressionNode[] arguments = lowerArguments(expression.arguments());
        return new SolvikNewNode(runtimeClasses.get(classSymbol), arguments);
    }

    private SolvikExpressionNode lowerConversion(CallExprNode expression, Type target) {
        SolvikExpressionNode argument = lowerExpression(expression.arguments().get(0));
        return new SolvikConvertNode(convertTarget(target), argument);
    }

    private static SolvikConvertNode.Target convertTarget(Type type) {
        if (type == ByteType.INSTANCE) {
            return SolvikConvertNode.Target.BYTE;
        }
        if (type == ShortType.INSTANCE) {
            return SolvikConvertNode.Target.SHORT;
        }
        if (type == IntType.INSTANCE) {
            return SolvikConvertNode.Target.INT;
        }
        if (type == LongType.INSTANCE) {
            return SolvikConvertNode.Target.LONG;
        }
        if (type == FloatType.INSTANCE) {
            return SolvikConvertNode.Target.FLOAT;
        }
        if (type == DoubleType.INSTANCE) {
            return SolvikConvertNode.Target.DOUBLE;
        }
        throw new IllegalStateException("unsupported conversion target " + type);
    }

    private SolvikExpressionNode lowerMethodCall(CallExprNode expression, ResolvedMethod resolved) {
        SolvikExpressionNode receiver;
        boolean safe = false;
        if (resolved.isImplicitThis()) {
            receiver = thisReceiver();
        } else {
            MemberAccessExprNode member = (MemberAccessExprNode) expression.callee();
            receiver = lowerExpression(member.receiver());
            safe = member.isSafe();
        }
        SolvikExpressionNode[] arguments = lowerArguments(expression.arguments());
        if (resolved.isSuperCall()) {
            SolvikFunction runtime = byDeclaration.get(resolved.method().declaration());
            if (runtime == null) {
                throw new IllegalStateException("no lowered method for '" + resolved.method().name() + "'");
            }
            return new SolvikInvokeMethodNode(runtime, receiver, arguments);
        }
        // Every other call dispatches on the receiver's runtime class table, whose entry is the
        // effective implementation: the class's own method, an inherited one, or a resolved interface
        // default. That is also what makes a call inside a default method body reach the concrete
        // implementor of a requirement rather than the interface. A `?.` call is guarded so the
        // arguments are not evaluated when the receiver is null.
        return new SolvikInvokeMethodNode(resolved.method().name(), receiver, arguments, safe);
    }

    private SolvikExpressionNode thisReceiver() {
        if (thisSlot < 0) {
            throw new IllegalStateException("'this' reached lowering outside a method or init");
        }
        return SolvikReadLocalVariableNodeGen.create(thisSlot);
    }

    private SolvikExpressionNode[] lowerArguments(List<ExpressionNode> arguments) {
        SolvikExpressionNode[] lowered = new SolvikExpressionNode[arguments.size()];
        for (int i = 0; i < lowered.length; i++) {
            lowered[i] = lowerExpression(arguments.get(i));
        }
        return lowered;
    }

    // ---------------------------------------------------------------------------------------------
    // Frame and source helpers
    // ---------------------------------------------------------------------------------------------

    private int allocateSlot(VariableSymbol variable) {
        Integer existing = slots.get(variable);
        if (existing != null) {
            return existing;
        }
        int slot = frameBuilder.addSlot(kindOf(variable.type()), variable.name(), null);
        slots.put(variable, slot);
        return slot;
    }

    private TruffleString propertyKey(PropertySymbol property) {
        SolvikClass owner = propertyOwners.get(property);
        if (owner == null) {
            throw new IllegalStateException("no runtime class for property '" + property.name() + "'");
        }
        return owner.propertyKey(property.index());
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static FrameSlotKind[] toKindArray(List<FrameSlotKind> values) {
        return values.toArray(FrameSlotKind[]::new);
    }

    private static FrameSlotKind kindOf(Type type) {
        if (type == IntType.INSTANCE) {
            return FrameSlotKind.Int;
        }
        if (type == BooleanType.INSTANCE) {
            return FrameSlotKind.Boolean;
        }
        if (type == LongType.INSTANCE) {
            return FrameSlotKind.Long;
        }
        if (type == FloatType.INSTANCE) {
            return FrameSlotKind.Float;
        }
        if (type == DoubleType.INSTANCE) {
            return FrameSlotKind.Double;
        }
        return FrameSlotKind.Object;
    }

    private <T extends SolvikStatementNode> T setSource(T node, AstNode ast) {
        SourceSpan span = ast.span();
        node.setSourceSection(span.startOffset(), span.length());
        return node;
    }
}
