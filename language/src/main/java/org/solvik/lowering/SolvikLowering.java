/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.solvik.ast.expression.BlockExprNode;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.CharacterLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IntegerLiteralNode;
import org.solvik.ast.expression.IfExprNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MapEntryExprNode;
import org.solvik.ast.expression.MatchBranchNode;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.NullLiteralNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.expression.SuperExprNode;
import org.solvik.ast.expression.SwitchExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.expression.TypeTestExprNode;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.ast.expression.UnaryOperator;
import org.solvik.ast.pattern.BindingPatternNode;
import org.solvik.ast.pattern.EnumPatternNode;
import org.solvik.ast.pattern.PatternNode;
import org.solvik.ast.pattern.WildcardPatternNode;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.BreakStmtNode;
import org.solvik.ast.statement.CaseLabelNode;
import org.solvik.ast.statement.ConstantCaseLabelNode;
import org.solvik.ast.statement.ContinueStmtNode;
import org.solvik.ast.statement.ElseBranchNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.ForInStmtNode;
import org.solvik.ast.statement.ForStmtNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.ast.statement.SwitchCaseNode;
import org.solvik.ast.statement.SwitchStmtNode;
import org.solvik.ast.statement.WhileStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.EnumSymbol;
import org.solvik.semantic.EnumVariantSymbol;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.InterfaceSymbol;
import org.solvik.semantic.PropertySymbol;
import org.solvik.semantic.ResolvedMethod;
import org.solvik.semantic.Symbol;
import org.solvik.semantic.VariableSymbol;
import org.solvik.regex.RegexPattern;
import org.solvik.source.SourceSpan;
import org.solvik.source.StringEscapes;
import org.solvik.truffle.SolvikEvalRootNode;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.nodes.SolvikCollectionConstructNode;
import org.solvik.truffle.SolvikLanguage;
import org.solvik.truffle.SolvikRootNode;
import org.solvik.truffle.nodes.SolvikAddNodeGen;
import org.solvik.truffle.nodes.SolvikBindingPatternNode;
import org.solvik.truffle.nodes.SolvikBlockExprNode;
import org.solvik.truffle.nodes.SolvikBlockNode;
import org.solvik.truffle.nodes.SolvikBoolLiteralNode;
import org.solvik.truffle.nodes.SolvikBreakNode;
import org.solvik.truffle.nodes.SolvikCastNode;
import org.solvik.truffle.nodes.SolvikCharacterLiteralNode;
import org.solvik.truffle.nodes.SolvikCoalesceNode;
import org.solvik.truffle.nodes.SolvikConcatNode;
import org.solvik.truffle.nodes.SolvikContinueNode;
import org.solvik.truffle.nodes.SolvikConvertNode;
import org.solvik.truffle.nodes.SolvikDivNodeGen;
import org.solvik.truffle.nodes.SolvikEnumConstructNode;
import org.solvik.truffle.nodes.SolvikEnumPatternNode;
import org.solvik.truffle.nodes.SolvikEqualNodeGen;
import org.solvik.truffle.nodes.SolvikEqualsCallNode;
import org.solvik.truffle.nodes.SolvikExitNode;
import org.solvik.truffle.nodes.SolvikHashCodeNode;
import org.solvik.truffle.nodes.SolvikExpressionNode;
import org.solvik.truffle.nodes.SolvikFloatingLiteralNode;
import org.solvik.truffle.nodes.SolvikForNode;
import org.solvik.truffle.nodes.SolvikForRangeNode;
import org.solvik.truffle.nodes.SolvikGreaterOrEqualNodeGen;
import org.solvik.truffle.nodes.SolvikGreaterThanNodeGen;
import org.solvik.truffle.nodes.SolvikIfNode;
import org.solvik.truffle.nodes.SolvikIfExprNode;
import org.solvik.truffle.nodes.SolvikIdentityHashCodeNode;
import org.solvik.truffle.nodes.SolvikIdentityNode;
import org.solvik.truffle.nodes.SolvikIntegerLiteralNode;
import org.solvik.truffle.nodes.SolvikInvokeMethodNode;
import org.solvik.truffle.nodes.SolvikInvokeNode;
import org.solvik.truffle.nodes.SolvikInvokeStaticNode;
import org.solvik.truffle.nodes.SolvikLessOrEqualNodeGen;
import org.solvik.truffle.nodes.SolvikLessThanNodeGen;
import org.solvik.truffle.nodes.SolvikLogicalAndNode;
import org.solvik.truffle.nodes.SolvikLogicalNotNodeGen;
import org.solvik.truffle.nodes.SolvikLogicalOrNode;
import org.solvik.truffle.nodes.SolvikLongLiteralNode;
import org.solvik.truffle.nodes.SolvikMatchClauseNode;
import org.solvik.truffle.nodes.SolvikMatchNode;
import org.solvik.truffle.nodes.SolvikMulNodeGen;
import org.solvik.truffle.nodes.SolvikNegateNodeGen;
import org.solvik.truffle.nodes.SolvikNewNode;
import org.solvik.truffle.nodes.SolvikNumericBinaryNode;
import org.solvik.truffle.nodes.SolvikNumericComparisonNode;
import org.solvik.truffle.nodes.SolvikNumericNegateNode;
import org.solvik.truffle.nodes.SolvikNullLiteralNode;
import org.solvik.truffle.nodes.SolvikPatternNode;
import org.solvik.truffle.nodes.SolvikPrintNode;
import org.solvik.truffle.nodes.SolvikPrintlnNode;
import org.solvik.truffle.nodes.SolvikReadLocalVariableNodeGen;
import org.solvik.truffle.nodes.SolvikReadPropertyNode;
import org.solvik.truffle.nodes.SolvikReadStaticPropertyNode;
import org.solvik.truffle.nodes.SolvikRegexCreateNode;
import org.solvik.truffle.nodes.SolvikRegexFindAllNode;
import org.solvik.truffle.nodes.SolvikRegexFindNode;
import org.solvik.truffle.nodes.SolvikRegexGroupNode;
import org.solvik.truffle.nodes.SolvikRegexLiteralNode;
import org.solvik.truffle.nodes.SolvikRegexMatchReadNode;
import org.solvik.truffle.nodes.SolvikRegexMatchesNode;
import org.solvik.truffle.nodes.SolvikRegexReplaceNode;
import org.solvik.truffle.nodes.SolvikReturnNode;
import org.solvik.truffle.nodes.SolvikStatementNode;
import org.solvik.truffle.nodes.SolvikStringLiteralNode;
import org.solvik.truffle.nodes.SolvikSubNodeGen;
import org.solvik.truffle.nodes.SolvikSuperConstructorNode;
import org.solvik.truffle.nodes.SolvikToStringNode;
import org.solvik.truffle.nodes.SolvikTypeTestNode;
import org.solvik.truffle.nodes.SolvikWhileNode;
import org.solvik.truffle.nodes.SolvikWildcardPatternNode;
import org.solvik.truffle.nodes.SolvikWriteLocalVariableNodeGen;
import org.solvik.truffle.nodes.SolvikWritePropertyNode;
import org.solvik.truffle.nodes.SolvikWriteStaticPropertyNode;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikEnumClass;
import org.solvik.truffle.object.SolvikEnumVariant;
import org.solvik.truffle.object.SolvikStaticCell;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharacterType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.IntegerType;
import org.solvik.type.LongType;
import org.solvik.type.BuiltinCollectionType;
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.TypeParameterType;
import org.solvik.type.NumericTypes;
import org.solvik.type.NullableType;
import org.solvik.type.ParameterizedType;
import org.solvik.type.RegexMatchType;
import org.solvik.type.RegexType;
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
 * <p>Frame slots are typed from the statically analysed binding types, so {@code Integer} and
 * {@code Boolean} locals and parameters use primitive frame storage. A method or constructor has an
 * implicit {@code this} receiver in frame slot zero; a constructor also runs declaration
 * initializers before the explicit constructor body.
 */
public final class SolvikLowering {

    private final CheckedProgram program;
    private final Map<Integer, Source> sourcesById;
    private final SolvikLanguage language;
    private final Map<String, SolvikFunction> runtimeFunctions = new LinkedHashMap<>();
    private final Map<FunctionSymbol, SolvikFunction> runtimeFunctionBySymbol = new IdentityHashMap<>();
    private final Map<FunctionDeclNode, SolvikFunction> byDeclaration = new IdentityHashMap<>();
    /** Runtime handles of compiler-synthesized delegation forwarding methods, keyed by symbol. */
    private final Map<FunctionSymbol, SolvikFunction> bySynthesizedSymbol = new IdentityHashMap<>();
    /** Synthesized forwarding methods already lowered, so an inherited one is compiled only once. */
    private final Set<FunctionSymbol> loweredSynthesized = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ClassSymbol, SolvikClass> runtimeClasses = new IdentityHashMap<>();
    /** Runtime class of a nominal class type, so a receiver lookup never depends on the simple name. */
    private final Map<ClassType, SolvikClass> runtimeClassByType = new IdentityHashMap<>();
    /** Runtime metadata of every enum variant, keyed by its compiler symbol. */
    private final Map<EnumVariantSymbol, SolvikEnumVariant> runtimeEnumVariants = new IdentityHashMap<>();
    private final Map<PropertySymbol, SolvikClass> propertyOwners = new IdentityHashMap<>();
    /**
     * The runtime class declaring each {@code static} method, keyed by its declaration. A static call
     * carries no receiver, so the declaring class is recorded here during lowering to trigger class
     * initialization at the call site (docs/LANGUAGE_SPEC.md section 7).
     */
    private final Map<FunctionDeclNode, SolvikClass> staticMethodOwners = new IdentityHashMap<>();
    private final Map<VariableSymbol, Integer> slots = new IdentityHashMap<>();
    private FrameDescriptor.Builder frameBuilder;
    private int thisSlot = -1;

    private SolvikLowering(CheckedProgram program, Map<Integer, Source> sourcesById, SolvikLanguage language) {
        this.program = program;
        this.sourcesById = sourcesById;
        this.language = language;
    }

    /** Lowers a checked program, returning its runtime representation. */
    public static LoweredProgram lower(CheckedProgram program, Map<Integer, Source> sourcesById, SolvikLanguage language) {
        return new SolvikLowering(program, sourcesById, language).run();
    }

    private LoweredProgram run() {
        for (EnumSymbol enumSymbol : program.enums().values()) {
            // Enum metadata exists before any body lowers so a construction node and an is/as test can
            // reference it. Runtime construction is statically resolved, so no variant name lookup is
            // needed; each variant still carries its owner for equality and display.
            SolvikEnumClass runtimeEnum = new SolvikEnumClass(enumSymbol.name(), enumSymbol.type());
            for (EnumVariantSymbol variant : enumSymbol.variants()) {
                runtimeEnumVariants.put(variant, new SolvikEnumVariant(runtimeEnum, variant.name(), variant.valueTypes().size()));
            }
        }
        for (FunctionSymbol function : program.functions().values()) {
            if (function.isBuiltin()) {
                continue;
            }
            SolvikFunction runtime = new SolvikFunction(function.name());
            String key = function.name();
            for (int i = 1; runtimeFunctions.containsKey(key); i++) {
                key = function.name() + "#" + i;
            }
            runtimeFunctions.put(key, runtime);
            runtimeFunctionBySymbol.put(function, runtime);
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
            runtimeClassByType.put(classSymbol.type(), runtimeClass);
            for (PropertySymbol property : classSymbol.properties()) {
                propertyOwners.put(property, runtimeClass);
            }
            for (PropertySymbol property : classSymbol.declaredStaticProperties()) {
                // One class-level cell per static property, seeded with its declared type's default so
                // a declaration without an initializer still reads a well-typed zero value
                // (docs/LANGUAGE_SPEC.md section 7).
                runtimeClass.installStaticCell(property.name(), new SolvikStaticCell(staticDefaultValue(property.type())));
                propertyOwners.put(property, runtimeClass);
            }
            for (FunctionSymbol method : classSymbol.declaredMethods()) {
                SolvikFunction runtime = new SolvikFunction(classSymbol.name() + "." + method.name());
                byDeclaration.put(method.declaration(), runtime);
            }
            for (FunctionSymbol method : classSymbol.declaredStaticMethods()) {
                // A static method handle is reached only through byDeclaration, never through the
                // runtime class table, because a static call is bound statically and is not inherited.
                byDeclaration.put(method.declaration(), new SolvikFunction(classSymbol.name() + "." + method.name()));
                staticMethodOwners.put(method.declaration(), runtimeClass);
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
            for (FunctionSymbol method : classSymbol.declaredStaticMethods()) {
                // A static method has no receiver, so it is lowered with no `this` slot.
                lowerCallable(method, false);
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
        lowerClassInitializers();
        CallTarget evalTarget = new SolvikEvalRootNode(language, entryPoint).getCallTarget();
        return new LoweredProgram(program, runtimeFunctions, evalTarget);
    }

    /**
     * Lowers and installs each class's initializer ({@code <clinit>}): the static property declaration
     * initializers in source order, then the class initializer block. A class with neither gets no
     * initializer, and its cells keep their type defaults. Initialization is lazy: the installed body
     * runs on the class's first active use, after its superclass chain, rather than in a fixed
     * program-start sequence, so a class is initialized identically no matter which other class
     * references it or where it is declared (docs/LANGUAGE_SPEC.md section 7).
     */
    private void lowerClassInitializers() {
        for (ClassSymbol classSymbol : program.classes().values()) {
            // Each initializer is its own callable: a class initializer block may declare locals, so the
            // frame is rebuilt per class exactly as lowerCallable does, and `this` stays unavailable.
            slots.clear();
            thisSlot = -1;
            frameBuilder = FrameDescriptor.newBuilder();
            List<SolvikStatementNode> statements = new ArrayList<>();
            for (PropertyDeclNode declaration : classSymbol.declaration().staticProperties()) {
                if (declaration.initializer().isEmpty()) {
                    continue;
                }
                PropertySymbol property = classSymbol.staticProperty(declaration.name()) //
                                .orElseThrow(() -> new IllegalStateException("no symbol for static property '" + declaration.name() + "'"));
                SolvikExpressionNode value = lowerExpression(declaration.initializer().get());
                SolvikClass runtimeClass = runtimeClasses.get(classSymbol);
                statements.add(setSource(new SolvikWriteStaticPropertyNode(runtimeClass, runtimeClass.staticCell(property.name()), value), declaration));
            }
            if (classSymbol.staticBlock().isPresent()) {
                statements.add(lowerBlock(classSymbol.staticBlock().get().body()));
            }
            if (statements.isEmpty()) {
                continue;
            }
            SourceSpan span = classSymbol.declaration().span();
            SolvikRootNode root = new SolvikRootNode(language, frameBuilder.build(), new SolvikBlockNode(statements.toArray(new SolvikStatementNode[0])), //
                            classSymbol.name() + ".<clinit>", false, new int[0], new FrameSlotKind[0], //
                            sourceFor(span), span.startOffset(), span.length());
            SolvikFunction initializer = new SolvikFunction(classSymbol.name() + ".<clinit>");
            initializer.install(root.getCallTarget());
            runtimeClasses.get(classSymbol).installClassInitializer(initializer);
        }
    }

    /**
     * The value a static cell holds before any initializer runs: the zero value of the declared type.
     * The boxed form matches the run-time representation the corresponding literal and conversion nodes
     * produce, which matters for the object-represented types ({@code Byte}, {@code Short},
     * {@code Character}) because semantic equality compares those values.
     */
    private static Object staticDefaultValue(Type type) {
        if (type == IntegerType.INSTANCE) {
            return 0;
        }
        if (type == LongType.INSTANCE) {
            return 0L;
        }
        if (type == FloatType.INSTANCE) {
            return 0.0f;
        }
        if (type == DoubleType.INSTANCE) {
            return 0.0;
        }
        if (type == BooleanType.INSTANCE) {
            return false;
        }
        if (type == ByteType.INSTANCE) {
            return (byte) 0;
        }
        if (type == ShortType.INSTANCE) {
            return (short) 0;
        }
        if (type == CharacterType.INSTANCE) {
            return '\0';
        }
        return null;
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
                        toIntArray(parameterSlots), toKindArray(parameterKinds), sourceFor(span), span.startOffset(), span.length());
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
                        toIntArray(parameterSlots), toKindArray(parameterKinds), sourceFor(span), span.startOffset(), span.length());
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
        body.setSourceSection(sourceFor(span), span.startOffset(), span.length());
        FrameDescriptor descriptor = frameBuilder.build();
        SolvikRootNode root = new SolvikRootNode(language, descriptor, body, function.name() + "(delegate)", returnsValue, //
                        toIntArray(parameterSlots), toKindArray(parameterKinds), sourceFor(span), span.startOffset(), span.length());
        bySynthesizedSymbol.get(function).install(root.getCallTarget());
    }

    /**
     * Builds a constructor body: every property and delegate declaration initializer runs first, in
     * source declaration order, then the explicit constructor body when present.
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
                // A `static` property is class-level storage and has no slot on the instance, so the
                // constructor body must not initialize it (docs/LANGUAGE_SPEC.md section 7).
                if (property.isStatic()) {
                    continue;
                }
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
            for (StatementNode statement : constructor.constructorDeclaration().body().statements()) {
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

    /** The sanctioned {@code super(...)} call opening a subclass constructor body, or {@code null}. */
    private static CallExprNode superCallOf(FunctionSymbol constructor) {
        List<StatementNode> statements = constructor.constructorDeclaration().body().statements();
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
            case FOR_IN_STMT -> lowerForIn((ForInStmtNode) statement);
            case SWITCH_STMT -> lowerSwitch((SwitchStmtNode) statement);
            case BLOCK -> lowerBlock((BlockNode) statement);
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
            SolvikExpressionNode value = lowerExpression(statement.value());
            if (property.index() == PropertySymbol.STATIC_SLOT) {
                // As for a static read, the class name target is not lowered as a value.
                SolvikClass staticOwner = staticOwnerOf(property);
                SolvikStatementNode node = new SolvikWriteStaticPropertyNode(staticOwner, staticOwner.staticCell(property.name()), value);
                return setSource(node, statement);
            }
            SolvikExpressionNode receiver = lowerExpression(member.receiver());
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

    /**
     * Lowers a range for-in loop. The bounds are lowered once into the loop node, which computes the
     * iteration count in {@code long} and writes the implicit {@code Integer} loop variable into its
     * frame slot before each body execution (docs/LANGUAGE_SPEC.md section 17).
     */
    private SolvikStatementNode lowerForIn(ForInStmtNode statement) {
        VariableSymbol variable = program.forInBindingOf(statement).orElseThrow(() -> new IllegalStateException("no symbol for a range for-in variable"));
        int loopSlot = allocateSlot(variable);
        SolvikExpressionNode start = lowerExpression(statement.start());
        SolvikExpressionNode end = lowerExpression(statement.end());
        SolvikStatementNode body = lowerBlock(statement.body());
        SolvikForRangeNode node = new SolvikForRangeNode(statement.operator(), start, end, loopSlot, body);
        return setSource(node, statement);
    }

    /**
     * Lowers a non-fallthrough {@code switch} statement to a stored scrutinee followed by an ordered
     * {@code if}/{@code else} chain. The chain tests each case's labels in source order and executes
     * exactly the first matching body, so no case can fall through and no {@code break} is needed.
     * The scrutinee is evaluated once into a frame slot and each label reads that slot.
     */
    private SolvikStatementNode lowerSwitch(SwitchStmtNode statement) {
        Type scrutineeType = program.typeOf(statement.scrutinee()).orElseThrow(() -> new IllegalStateException("no type for a switch scrutinee"));
        int scrutineeSlot = frameBuilder.addSlot(kindOf(scrutineeType), "switch", null);
        List<SolvikStatementNode> statements = new ArrayList<>();
        statements.add(SolvikWriteLocalVariableNodeGen.create(lowerExpression(statement.scrutinee()), scrutineeSlot));
        SolvikStatementNode elseBranch = null;
        List<SwitchCaseNode> cases = statement.cases();
        // Build from the last case to the first so each earlier case wraps the ones after it. The
        // semantic pass has already guaranteed that a default, if present, is last.
        for (int i = cases.size() - 1; i >= 0; i--) {
            SwitchCaseNode switchCase = cases.get(i);
            SolvikStatementNode body = lowerBlock(switchCase.body());
            if (switchCase.isDefault()) {
                elseBranch = body;
                continue;
            }
            SolvikExpressionNode condition = null;
            for (CaseLabelNode label : switchCase.labels()) {
                SolvikExpressionNode test = lowerCaseLabelTest(label, scrutineeSlot);
                condition = condition == null ? test : new SolvikLogicalOrNode(condition, test);
            }
            elseBranch = new SolvikIfNode(condition, body, elseBranch);
        }
        if (elseBranch != null) {
            statements.add(elseBranch);
        }
        return setSource(new SolvikBlockNode(statements.toArray(SolvikStatementNode[]::new)), statement);
    }

    /** Lowers one case-label test: value equality for a constant, or a full regex match for a regex case. */
    private SolvikExpressionNode lowerCaseLabelTest(CaseLabelNode label, int scrutineeSlot) {
        if (label instanceof ConstantCaseLabelNode constant) {
            SolvikExpressionNode scrutinee = SolvikReadLocalVariableNodeGen.create(scrutineeSlot);
            return SolvikEqualNodeGen.create(scrutinee, lowerExpression(constant.expression()));
        }
        RegexCaseLabelNode regex = (RegexCaseLabelNode) label;
        RegexPattern pattern = program.regexCasePatternOf(regex).orElseThrow(() -> new IllegalStateException("no compiled pattern for a switch regex case"));
        SolvikExpressionNode scrutinee = SolvikReadLocalVariableNodeGen.create(scrutineeSlot);
        // A regex case requires a complete match, matching Regex.matches (docs/LANGUAGE_SPEC.md section 14).
        return new SolvikRegexMatchesNode(new SolvikRegexLiteralNode(pattern), scrutinee, false);
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
            case INTEGER_LITERAL -> new SolvikIntegerLiteralNode(Integer.parseInt(((IntegerLiteralNode) expression).lexeme()));
            case LONG_LITERAL -> lowerLongLiteral((LongLiteralNode) expression);
            case FLOATING_LITERAL -> lowerFloatingLiteral((FloatingLiteralNode) expression);
            case CHARACTER_LITERAL -> new SolvikCharacterLiteralNode(decodeCharacter(((CharacterLiteralNode) expression).lexeme()));
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
            case NAMESPACE_ACCESS_EXPR -> throw new IllegalStateException("a module-qualified name is not a value");
            case MATCH_EXPR -> lowerMatch((MatchExprNode) expression);
            case BLOCK_EXPR -> lowerBlockExpr((BlockExprNode) expression);
            case IF_EXPR -> lowerIfExpr((IfExprNode) expression);
            case SWITCH_EXPR -> lowerSwitchExpr((SwitchExprNode) expression);
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
    private static char decodeCharacter(String lexeme) {
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
            throw new IllegalStateException("'this' reached lowering outside a method or constructor");
        }
        return SolvikReadLocalVariableNodeGen.create(thisSlot);
    }

    private SolvikExpressionNode lowerMemberRead(MemberAccessExprNode member) {
        if (program.variantOf(member).isPresent()) {
            return lowerEnumConstruction(member);
        }
        CollectionContext collection = collectionContext(program.typeOf(member.receiver()).orElse(null));
        if (collection != null) {
            // A collection property (currently only the computed size) executes through the shared
            // invoke node, which dispatches to the built-in collection receiver.
            SolvikExpressionNode[] empty = new SolvikExpressionNode[0];
            return new SolvikInvokeMethodNode(member.memberName(), lowerExpression(member.receiver()), empty, member.isSafe());
        }
        Type receiverBase = baseTypeOf(program.typeOf(member.receiver()).orElse(null));
        if (receiverBase == RegexMatchType.INSTANCE) {
            SolvikRegexMatchReadNode.Field field = regexMatchField(member.memberName());
            if (field != null) {
                return new SolvikRegexMatchReadNode(field, lowerExpression(member.receiver()), member.isSafe());
            }
        }
        PropertySymbol property = program.propertyOf(member).orElseThrow(() -> new IllegalStateException("no property for member read"));
        if (property.index() == PropertySymbol.STATIC_SLOT) {
            // A read whose receiver is a class name: the cell is the declaring class's, resolved now,
            // and the class name itself contributes no run-time evaluation.
            SolvikClass staticOwner = staticOwnerOf(property);
            return new SolvikReadStaticPropertyNode(staticOwner, staticOwner.staticCell(property.name()));
        }
        SolvikExpressionNode receiver = member.receiver() instanceof SuperExprNode ? thisReceiver() : lowerExpression(member.receiver());
        return new SolvikReadPropertyNode(receiver, propertyKey(property), member.isSafe());
    }

    /**
     * The runtime class owning a static property's cell, checked to actually hold that cell. The cell
     * and its owner are both resolved during lowering, so a static access performs no run-time lookup,
     * and the owner is carried so the access can initialize the class on first active use.
     */
    private SolvikClass staticOwnerOf(PropertySymbol property) {
        SolvikClass owner = propertyOwners.get(property);
        if (owner == null) {
            throw new IllegalStateException("no owning class for static property '" + property.name() + "'");
        }
        if (owner.staticCell(property.name()) == null) {
            throw new IllegalStateException("no storage cell for static property '" + property.name() + "'");
        }
        return owner;
    }

    /** The non-null base of a possibly nullable receiver type. */
    private static Type baseTypeOf(Type type) {
        return type instanceof NullableType nullable ? nullable.inner() : type;
    }

    /** The {@code RegexMatch} property a member name denotes, or {@code null} for a method or an unknown name. */
    private static SolvikRegexMatchReadNode.Field regexMatchField(String name) {
        return switch (name) {
            case "value" -> SolvikRegexMatchReadNode.Field.VALUE;
            case "start" -> SolvikRegexMatchReadNode.Field.START;
            case "end" -> SolvikRegexMatchReadNode.Field.END;
            case "groupCount" -> SolvikRegexMatchReadNode.Field.GROUP_COUNT;
            default -> null;
        };
    }

    /** The collection context behind a statically recorded type, or {@code null} when it is not a collection. */
    private static CollectionContext collectionContext(Type type) {
        // Unwrap a nullable receiver: `nums?.size` on a `List<Integer>?` must still reach the
        // collection invoke so the shared dispatch handles the built-in receiver (and the safe
        // flag yields null when the receiver is null). baseTypeOf already unwraps nullability.
        if (baseTypeOf(type) instanceof ParameterizedType parameterized && parameterized.base() instanceof BuiltinCollectionType collection) {
            return new CollectionContext(collection, parameterized.substitution());
        }
        return null;
    }

    /** A resolved built-in collection receiver and the substitution binding its type parameters. */
    private static final class CollectionContext {
        private final BuiltinCollectionType type;
        private final Map<TypeParameterType, Type> substitution;

        CollectionContext(BuiltinCollectionType type, Map<TypeParameterType, Type> substitution) {
            this.type = type;
            this.substitution = substitution;
        }

        BuiltinCollectionType collectionType() {
            return type;
        }

        /**
         * Whether the constructed collection stores elements in a primitive array. Only a
         * {@code List} whose element type is {@code Integer} qualifies; every other kind (a
         * {@code List} of a non-{@code Integer} type, a {@code Set}, a {@code Map}, a {@code Stack})
         * is stored as boxed objects.
         */
        boolean isIntegral() {
            return type.name().equals("List") && substitution.get(type.typeParameter(0)) instanceof IntegerType;
        }
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
        return runtimeClassByType.get(target);
    }

    private SolvikExpressionNode lowerUnary(UnaryExprNode expression) {
        SolvikExpressionNode operand = lowerExpression(expression.operand());
        if (expression.operator() == UnaryOperator.NOT) {
            return SolvikLogicalNotNodeGen.create(operand);
        }
        if (program.typeOf(expression.operand()).orElse(null) == IntegerType.INSTANCE) {
            return SolvikNegateNodeGen.create(operand);
        }
        return new SolvikNumericNegateNode(operand);
    }

    private SolvikExpressionNode lowerBinary(BinaryExprNode expression) {
        SolvikExpressionNode left = lowerExpression(expression.left());
        SolvikExpressionNode right = lowerExpression(expression.right());
        BinaryOperator operator = expression.operator();
        if (operator == BinaryOperator.CONCAT) {
            // `..` renders each operand through toString, so any value may be concatenated.
            return new SolvikConcatNode(new SolvikToStringNode(left, false), new SolvikToStringNode(right, false));
        }
        if (operator == BinaryOperator.COALESCE) {
            // Null coalescing short-circuits the right operand; static analysis needs the left nullable.
            return new SolvikCoalesceNode(left, right);
        }
        Type operandType = program.typeOf(expression.left()).orElse(null);
        // Non-Integer numeric types use the generic numeric nodes; Integer and String keep the specialized
        // Phase 5 nodes.
        if (NumericTypes.isNumeric(operandType) && operandType != IntegerType.INSTANCE) {
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
            case CONCAT -> throw new IllegalStateException("concat is lowered before the operator switch");
            case LT -> SolvikLessThanNodeGen.create(left, right);
            case LE -> SolvikLessOrEqualNodeGen.create(left, right);
            case GT -> SolvikGreaterThanNodeGen.create(left, right);
            case GE -> SolvikGreaterOrEqualNodeGen.create(left, right);
            case EQ -> SolvikEqualNodeGen.create(left, right);
            case NEQ -> SolvikLogicalNotNodeGen.create(SolvikEqualNodeGen.create(left, right));
            case EQEQ -> new SolvikIdentityNode(left, right);
            case NEQEQ -> SolvikLogicalNotNodeGen.create(new SolvikIdentityNode(left, right));
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
        if (program.variantOf(expression).isPresent()) {
            return lowerEnumConstruction(expression);
        }
        FunctionSymbol qualifiedFunction = program.qualifiedFunctionOf(expression.callee()).orElse(null);
        if (qualifiedFunction != null) {
            SolvikFunction runtime = runtimeFunctionBySymbol.get(qualifiedFunction);
            if (runtime == null) {
                throw new IllegalStateException("no lowered function for '" + qualifiedFunction.name() + "'");
            }
            return new SolvikInvokeNode(runtime, lowerArguments(expression.arguments()));
        }
        if (expression.callee() instanceof NameRefExprNode callee) {
            CollectionContext context = collectionContext(program.typeOf(callee).orElse(null));
            if (context != null) {
                return new SolvikCollectionConstructNode(context.collectionType(), context.isIntegral(), lowerCollectionArguments(expression, context.collectionType()));
            }
        }
        if (expression.callee() instanceof NameRefExprNode regexName && "Regex".equals(regexName.name()) && program.symbolOf(regexName).isEmpty()) {
            // Built-in Regex construction. A source constant carries the pattern compiled once by
            // static analysis; any other pattern is validated and compiled on first execution.
            RegexPattern constant = program.regexConstantOf(expression).orElse(null);
            if (constant != null) {
                return new SolvikRegexLiteralNode(constant);
            }
            return new SolvikRegexCreateNode(lowerExpression(expression.arguments().get(0)));
        }
        // The universal `Any.toString`/`Any.equals` members are resolved before any per-type member
        // table, so they must be lowered before the built-in Regex/RegexMatch member dispatch.
        if (program.isBuiltinToString(expression)) {
            MemberAccessExprNode member = (MemberAccessExprNode) expression.callee();
            return new SolvikToStringNode(lowerExpression(member.receiver()), member.isSafe());
        }
        if (program.isBuiltinEquals(expression)) {
            MemberAccessExprNode member = (MemberAccessExprNode) expression.callee();
            // The single argument is checked by analysis; the receiver is the dynamic equality
            // receiver, matching the operator and an explicit equals call (section 3).
            if (member.receiver() instanceof SuperExprNode) {
                // `super.equals` with no source override reaches the root identity default, so it
                // compares `this` against the argument and never re-dispatches to this class's own
                // override.
                return new SolvikIdentityNode(thisReceiver(), lowerExpression(expression.arguments().get(0)));
            }
            return new SolvikEqualsCallNode(lowerExpression(member.receiver()), lowerExpression(expression.arguments().get(0)), member.isSafe());
        }
        // The universal Any.hashCode(): Integer is resolved before any per-type member table, so a
        // collection receiver reaches the root hash service instead of the erased invoke (which
        // knows only declared members and has no hashCode case). A super receiver with no override
        // reaches the same root identity default that super.equals reaches.
        if (program.isBuiltinHashCode(expression)) {
            MemberAccessExprNode member = (MemberAccessExprNode) expression.callee();
            if (member.receiver() instanceof SuperExprNode) {
                return new SolvikIdentityHashCodeNode(thisReceiver());
            }
            return new SolvikHashCodeNode(lowerExpression(member.receiver()), member.isSafe());
        }
        // A built-in collection receiver has no method table, so dispatch its declared members
        // through its single invoke. The universal Any.toString/Any.equals/Any.hashCode members
        // resolve above, so equals/toString/hashCode on a collection reach the root services,
        // not the erased table.
        if (expression.callee() instanceof MemberAccessExprNode member && (collectionContext(program.typeOf(member.receiver()).orElse(null)) != null)) {
            SolvikExpressionNode receiver = lowerExpression(member.receiver());
            SolvikExpressionNode[] arguments = lowerArguments(expression.arguments());
            return new SolvikInvokeMethodNode(member.memberName(), receiver, arguments, member.isSafe());
        }
        if (expression.callee() instanceof MemberAccessExprNode regexReceiver) {
            Type receiverBase = baseTypeOf(program.typeOf(regexReceiver.receiver()).orElse(null));
            if (receiverBase == RegexType.INSTANCE || receiverBase == RegexMatchType.INSTANCE) {
                return lowerRegexMemberCall(expression, regexReceiver, receiverBase);
            }
        }
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
                case "print" -> new SolvikPrintNode(new SolvikToStringNode(arguments[0], false));
                case "println" -> new SolvikPrintlnNode(new SolvikToStringNode(arguments[0], false));
                case "exit" -> new SolvikExitNode(arguments[0]);
                default -> throw new IllegalStateException("unknown built-in '" + function.name() + "'");
            };
        }
        SolvikFunction runtime = runtimeFunctionBySymbol.get(function);
        if (runtime == null) {
            throw new IllegalStateException("no lowered function for '" + function.name() + "'");
        }
        return new SolvikInvokeNode(runtime, arguments);
    }

    private SolvikExpressionNode lowerConstruction(CallExprNode expression, ClassSymbol classSymbol) {
        SolvikExpressionNode[] arguments = lowerArguments(expression.arguments());
        SolvikClass runtimeClass = runtimeClasses.get(classSymbol);
        return new SolvikNewNode(runtimeClass, arguments);
    }

    /**
     * Lowers one of the built-in {@code Regex} or {@code RegexMatch} member calls
     * (docs/LANGUAGE_SPEC.md section 14). Static analysis already checked arity and argument types,
     * so lowering emits the dedicated node for the resolved member.
     */
    private SolvikExpressionNode lowerRegexMemberCall(CallExprNode call, MemberAccessExprNode member, Type receiverType) {
        SolvikExpressionNode receiver = lowerExpression(member.receiver());
        boolean safe = member.isSafe();
        if (receiverType == RegexType.INSTANCE) {
            return switch (member.memberName()) {
                case "matches" -> new SolvikRegexMatchesNode(receiver, lowerExpression(call.arguments().get(0)), safe);
                case "find" -> new SolvikRegexFindNode(receiver, lowerExpression(call.arguments().get(0)), safe);
                case "findAll" -> new SolvikRegexFindAllNode(receiver, lowerExpression(call.arguments().get(0)), safe);
                case "replace" -> new SolvikRegexReplaceNode(receiver, lowerExpression(call.arguments().get(0)), lowerExpression(call.arguments().get(1)), safe);
                default -> throw new IllegalStateException("unknown Regex member '" + member.memberName() + "'");
            };
        }
        if ("group".equals(member.memberName())) {
            return new SolvikRegexGroupNode(receiver, lowerExpression(call.arguments().get(0)), safe);
        }
        throw new IllegalStateException("unknown RegexMatch member '" + member.memberName() + "'");
    }

    /**
     * Lowers a statically resolved enum variant construction (docs/LANGUAGE_SPEC.md section 12). A
     * call carries positional values; a value-less variant read carries none. The variant metadata
     * was created before any body was lowered.
     */
    private SolvikExpressionNode lowerEnumConstruction(ExpressionNode expression) {
        EnumVariantSymbol variant = program.variantOf(expression).orElseThrow(() -> new IllegalStateException("no variant for enum construction"));
        SolvikExpressionNode[] values = expression instanceof CallExprNode call ? lowerArguments(call.arguments()) : new SolvikExpressionNode[0];
        SolvikEnumVariant runtimeVariant = runtimeEnumVariants.get(variant);
        if (runtimeVariant == null) {
            throw new IllegalStateException("no runtime variant for '" + variant.name() + "'");
        }
        return new SolvikEnumConstructNode(runtimeVariant, values);
    }

    // ---------------------------------------------------------------------------------------------
    // Exhaustive match
    // ---------------------------------------------------------------------------------------------

    /** Lowers a match expression to a scrutinee evaluation plus ordered pattern/result clauses. */
    private SolvikExpressionNode lowerMatch(MatchExprNode expression) {
        SolvikExpressionNode scrutinee = lowerExpression(expression.scrutinee());
        SolvikMatchClauseNode[] clauses = new SolvikMatchClauseNode[expression.branches().size()];
        for (int i = 0; i < clauses.length; i++) {
            MatchBranchNode branch = expression.branches().get(i);
            // The pattern runs first so every binding slot exists with object representation before
            // the result expression is lowered and reads it.
            SolvikPatternNode pattern = lowerPattern(branch.pattern());
            SolvikExpressionNode result = lowerExpression(branch.result());
            clauses[i] = new SolvikMatchClauseNode(pattern, result);
        }
        return new SolvikMatchNode(scrutinee, clauses);
    }

    /** Lowers one pattern, allocating an object frame slot for every name it binds. */
    private SolvikPatternNode lowerPattern(PatternNode pattern) {
        if (pattern instanceof WildcardPatternNode) {
            return new SolvikWildcardPatternNode();
        }
        if (pattern instanceof BindingPatternNode binding) {
            VariableSymbol variable = program.patternBindingOf(binding).orElseThrow(() -> new IllegalStateException("no symbol for a match binding pattern"));
            int slot = allocateObjectSlot(variable);
            Type target = program.bindingTypeOf(binding).orElse(null);
            SolvikClass targetClass = target == null ? null : runtimeClassOf(target);
            return new SolvikBindingPatternNode(slot, target, targetClass);
        }
        EnumPatternNode enumPattern = (EnumPatternNode) pattern;
        EnumVariantSymbol variant = program.enumPatternOf(enumPattern).orElseThrow(() -> new IllegalStateException("no variant for an enum pattern"));
        SolvikEnumVariant runtimeVariant = runtimeEnumVariants.get(variant);
        if (runtimeVariant == null) {
            throw new IllegalStateException("no runtime variant for '" + variant.name() + "'");
        }
        SolvikPatternNode[] arguments = new SolvikPatternNode[enumPattern.arguments().size()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = lowerPattern(enumPattern.arguments().get(i));
        }
        return new SolvikEnumPatternNode(runtimeVariant, arguments);
    }

    /** Allocates an object frame slot for a match binding, whose value is always object-represented. */
    private int allocateObjectSlot(VariableSymbol variable) {
        Integer existing = slots.get(variable);
        if (existing != null) {
            return existing;
        }
        int slot = frameBuilder.addSlot(FrameSlotKind.Object, variable.name(), null);
        slots.put(variable, slot);
        return slot;
    }

    // ---------------------------------------------------------------------------------------------
    // Value-producing block, if, and switch expressions
    // ---------------------------------------------------------------------------------------------

    /** Lowers a block expression by reusing the shared value-block lowering. */
    private SolvikExpressionNode lowerBlockExpr(BlockExprNode expression) {
        return lowerValueBlockBody(expression.body());
    }

    /**
     * Lowers a value-required block: its statements run in the enclosing frame and its tail produces
     * the value. A validated value block always has a tail except when every path transfers control,
     * in which case the tail is absent and the node is never reached with a value.
     */
    private SolvikExpressionNode lowerValueBlock(BlockNode block) {
        SolvikExpressionNode node = lowerValueBlockBody(block);
        setSource(node, block);
        return node;
    }

    private SolvikBlockExprNode lowerValueBlockBody(BlockNode block) {
        SolvikStatementNode[] statements = new SolvikStatementNode[block.statements().size()];
        for (int i = 0; i < statements.length; i++) {
            statements[i] = lowerStatement(block.statements().get(i));
        }
        SolvikExpressionNode tail = block.tail().map(this::lowerExpression).orElse(null);
        return new SolvikBlockExprNode(statements, tail);
    }

    /** Lowers an {@code if} expression to a single condition test and two value branches. */
    private SolvikExpressionNode lowerIfExpr(IfExprNode expression) {
        SolvikExpressionNode condition = lowerExpression(expression.condition());
        SolvikExpressionNode thenValue = lowerValueBlock(expression.thenBlock());
        SolvikExpressionNode elseValue = expression.elseValue().map(this::lowerExpression).orElse(null);
        return new SolvikIfExprNode(condition, thenValue, elseValue);
    }

    /**
     * Lowers a {@code switch} expression to a stored scrutinee followed by an ordered
     * {@code if}/{@code else} expression chain. The scrutinee is evaluated exactly once; each case
     * tests its labels in source order and the selected body produces the value. The semantic layer
     * has guaranteed exactly one last {@code default}, so the chain always has a fallback.
     */
    private SolvikExpressionNode lowerSwitchExpr(SwitchExprNode expression) {
        Type scrutineeType = program.typeOf(expression.scrutinee()).orElseThrow(() -> new IllegalStateException("no type for a switch expression scrutinee"));
        int scrutineeSlot = frameBuilder.addSlot(kindOf(scrutineeType), "switch", null);
        SolvikStatementNode store = SolvikWriteLocalVariableNodeGen.create(lowerExpression(expression.scrutinee()), scrutineeSlot);
        SolvikExpressionNode elseValue = null;
        List<SwitchCaseNode> cases = expression.cases();
        for (int i = cases.size() - 1; i >= 0; i--) {
            SwitchCaseNode switchCase = cases.get(i);
            SolvikExpressionNode body = lowerValueBlock(switchCase.body());
            if (switchCase.isDefault()) {
                elseValue = body;
                continue;
            }
            SolvikExpressionNode condition = null;
            for (CaseLabelNode label : switchCase.labels()) {
                SolvikExpressionNode test = lowerCaseLabelTest(label, scrutineeSlot);
                condition = condition == null ? test : new SolvikLogicalOrNode(condition, test);
            }
            elseValue = new SolvikIfExprNode(condition, body, elseValue);
        }
        return new SolvikBlockExprNode(new SolvikStatementNode[]{store}, elseValue);
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
        if (type == IntegerType.INSTANCE) {
            return SolvikConvertNode.Target.INTEGER;
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
        FunctionSymbol method = resolved.method();
        if (method.isStatic()) {
            // A static method has no receiver and no virtual dispatch: the declaring class's
            // implementation is bound directly, and any written class-name receiver contributes no
            // run-time evaluation. The call is an active use, so the declaring class is carried to
            // initialize it before the arguments are evaluated (docs/LANGUAGE_SPEC.md section 7).
            SolvikFunction runtime = byDeclaration.get(method.declaration());
            if (runtime == null) {
                throw new IllegalStateException("no lowered method for '" + method.name() + "'");
            }
            SolvikClass owner = staticMethodOwners.get(method.declaration());
            if (owner == null) {
                throw new IllegalStateException("no owning class for static method '" + method.name() + "'");
            }
            return new SolvikInvokeStaticNode(owner, runtime, lowerArguments(expression.arguments()));
        }
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
            throw new IllegalStateException("'this' reached lowering outside a method or constructor");
        }
        return SolvikReadLocalVariableNodeGen.create(thisSlot);
    }

    /**
     * Lowers the value arguments of a collection construction. A {@code Map} pairs each
     * {@code key: value} entry into a flat key/value sequence, which
     * {@link SolvikCollectionConstructNode} splits into the map's parallel key and value arrays; every
     * other collection lowers its elements in order.
     */
    private SolvikExpressionNode[] lowerCollectionArguments(CallExprNode call, BuiltinCollectionType collection) {
        if (collection != BuiltinCollectionTypes.MAP) {
            return lowerArguments(call.arguments());
        }
        SolvikExpressionNode[] flattened = new SolvikExpressionNode[call.arguments().size() * 2];
        int index = 0;
        for (ExpressionNode argument : call.arguments()) {
            if (!(argument instanceof MapEntryExprNode entry)) {
                throw new IllegalStateException("a Map construction reached lowering with a non-entry argument");
            }
            flattened[index++] = lowerExpression(entry.key());
            flattened[index++] = lowerExpression(entry.value());
        }
        return flattened;
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
        if (type == IntegerType.INSTANCE) {
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
        node.setSourceSection(sourceFor(span), span.startOffset(), span.length());
        return node;
    }

    private Source sourceFor(SourceSpan span) {
        Source source = sourcesById.get(span.sourceId());
        if (source == null) {
            throw new IllegalStateException("no Truffle source registered for source id " + span.sourceId());
        }
        return source;
    }
}
