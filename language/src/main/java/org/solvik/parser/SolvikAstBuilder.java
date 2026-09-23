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
package org.solvik.parser;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.DelegateDeclNode;
import org.solvik.ast.declaration.EnumDeclNode;
import org.solvik.ast.declaration.EnumVariantNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.ConstructorDeclNode;
import org.solvik.ast.declaration.IncludeDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.ModuleDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;
import org.solvik.ast.declaration.TypeParameterNode;
import org.solvik.ast.declaration.TypeRefNode;
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
import org.solvik.ast.expression.LiteralNode;
import org.solvik.ast.expression.MapEntryExprNode;
import org.solvik.ast.expression.MatchBranchNode;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.NamespaceAccessExprNode;
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
import org.solvik.ast.statement.BindingKind;
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
import org.solvik.ast.statement.RangeOperator;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.ast.statement.SwitchCaseNode;
import org.solvik.ast.statement.SwitchStmtNode;
import org.solvik.ast.statement.WhileStmtNode;
import org.solvik.parser.generated.SolvikParser.AdditiveContext;
import org.solvik.parser.generated.SolvikParser.ArgumentListContext;
import org.solvik.parser.generated.SolvikParser.AssignableContext;
import org.solvik.parser.generated.SolvikParser.BlockContext;
import org.solvik.parser.generated.SolvikParser.BlockExprContext;
import org.solvik.parser.generated.SolvikParser.BoolLiteralContext;
import org.solvik.parser.generated.SolvikParser.BreakStmtContext;
import org.solvik.parser.generated.SolvikParser.CallArgumentContext;
import org.solvik.parser.generated.SolvikParser.CallSuffixContext;
import org.solvik.parser.generated.SolvikParser.CaseLabelContext;
import org.solvik.parser.generated.SolvikParser.CharacterLiteralContext;
import org.solvik.parser.generated.SolvikParser.ClassDeclContext;
import org.solvik.parser.generated.SolvikParser.ClassMemberContext;
import org.solvik.parser.generated.SolvikParser.CompilationUnitContext;
import org.solvik.parser.generated.SolvikParser.ConcatContext;
import org.solvik.parser.generated.SolvikParser.ContinueStmtContext;
import org.solvik.parser.generated.SolvikParser.DefaultCaseContext;
import org.solvik.parser.generated.SolvikParser.DefaultMethodDeclContext;
import org.solvik.parser.generated.SolvikParser.DelegateDeclContext;
import org.solvik.parser.generated.SolvikParser.ElseBranchContext;
import org.solvik.parser.generated.SolvikParser.ElseExprBranchContext;
import org.solvik.parser.generated.SolvikParser.EnumDeclContext;
import org.solvik.parser.generated.SolvikParser.EnumVariantContext;
import org.solvik.parser.generated.SolvikParser.EqualityContext;
import org.solvik.parser.generated.SolvikParser.ExprStmtContext;
import org.solvik.parser.generated.SolvikParser.ExpressionContext;
import org.solvik.parser.generated.SolvikParser.FloatingLiteralContext;
import org.solvik.parser.generated.SolvikParser.ForInStmtContext;
import org.solvik.parser.generated.SolvikParser.ForInitContext;
import org.solvik.parser.generated.SolvikParser.ForStmtContext;
import org.solvik.parser.generated.SolvikParser.FunctionDeclContext;
import org.solvik.parser.generated.SolvikParser.IfExprContext;
import org.solvik.parser.generated.SolvikParser.IfStmtContext;
import org.solvik.parser.generated.SolvikParser.IncludeDeclContext;
import org.solvik.parser.generated.SolvikParser.ConstructorDeclContext;
import org.solvik.parser.generated.SolvikParser.InterfaceDeclContext;
import org.solvik.parser.generated.SolvikParser.InterfaceMemberContext;
import org.solvik.parser.generated.SolvikParser.IntegerLiteralContext;
import org.solvik.parser.generated.SolvikParser.LocalDeclContext;
import org.solvik.parser.generated.SolvikParser.LocalDeclNoSemiContext;
import org.solvik.parser.generated.SolvikParser.LogicalAndContext;
import org.solvik.parser.generated.SolvikParser.LogicalOrContext;
import org.solvik.parser.generated.SolvikParser.LongLiteralContext;
import org.solvik.parser.generated.SolvikParser.MatchBranchContext;
import org.solvik.parser.generated.SolvikParser.MatchExprContext;
import org.solvik.parser.generated.SolvikParser.MemberSuffixContext;
import org.solvik.parser.generated.SolvikParser.NamespaceSuffixContext;
import org.solvik.parser.generated.SolvikParser.MethodDeclContext;
import org.solvik.parser.generated.SolvikParser.ModuleDeclContext;
import org.solvik.parser.generated.SolvikParser.MethodModifierContext;
import org.solvik.parser.generated.SolvikParser.MultiplicativeContext;
import org.solvik.parser.generated.SolvikParser.NameContext;
import org.solvik.parser.generated.SolvikParser.NullCoalescingContext;
import org.solvik.parser.generated.SolvikParser.NullLiteralContext;
import org.solvik.parser.generated.SolvikParser.ParameterContext;
import org.solvik.parser.generated.SolvikParser.ParenContext;
import org.solvik.parser.generated.SolvikParser.PatternContext;
import org.solvik.parser.generated.SolvikParser.PatternListContext;
import org.solvik.parser.generated.SolvikParser.PostfixContext;
import org.solvik.parser.generated.SolvikParser.PrimaryContext;
import org.solvik.parser.generated.SolvikParser.PropertyDeclContext;
import org.solvik.parser.generated.SolvikParser.RangeExprContext;
import org.solvik.parser.generated.SolvikParser.RawStringLiteralContext;
import org.solvik.parser.generated.SolvikParser.RegexCaseLabelContext;
import org.solvik.parser.generated.SolvikParser.RelationContext;
import org.solvik.parser.generated.SolvikParser.RelationalContext;
import org.solvik.parser.generated.SolvikParser.ReturnStmtContext;
import org.solvik.parser.generated.SolvikParser.SignatureDeclContext;
import org.solvik.parser.generated.SolvikParser.StatementContext;
import org.solvik.parser.generated.SolvikParser.StringLiteralContext;
import org.solvik.parser.generated.SolvikParser.SuffixContext;
import org.solvik.parser.generated.SolvikParser.SuperExprContext;
import org.solvik.parser.generated.SolvikParser.SwitchCaseContext;
import org.solvik.parser.generated.SolvikParser.SwitchExprContext;
import org.solvik.parser.generated.SolvikParser.SwitchStmtContext;
import org.solvik.parser.generated.SolvikParser.ThisExprContext;
import org.solvik.parser.generated.SolvikParser.TypeArgumentsContext;
import org.solvik.parser.generated.SolvikParser.TypeParameterListContext;
import org.solvik.parser.generated.SolvikParser.TypeRefContext;
import org.solvik.parser.generated.SolvikParser.UnaryContext;
import org.solvik.parser.generated.SolvikParser.ValueBlockContext;
import org.solvik.parser.generated.SolvikParser.ValueCaseBodyContext;
import org.solvik.parser.generated.SolvikParser.ValueDefaultCaseContext;
import org.solvik.parser.generated.SolvikParser.ValueSwitchCaseContext;
import org.solvik.parser.generated.SolvikParser.ValueTailContext;
import org.solvik.parser.generated.SolvikParser.WhileStmtContext;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/**
 * Builds immutable Solvik syntax-AST nodes from the generated ANTLR parse tree.
 *
 * <p>The builder runs only when parsing produced no lexer/parser errors (the caller checks first),
 * so it treats the tree as structurally valid and performs no recovery. Mapping is exact: binary
 * precedence is encoded by grammar nesting, parentheses remain syntactic nodes, assignment is a
 * statement node (never an expression), and every node receives a half-open span
 * {@code [startToken.start, stopToken.stop + 1)} of zero-based character offsets.
 *
 * <p>Declaration nodes reached through {@code localDecl} include their trailing {@code ;} because
 * the grammar consumes it; the {@code for}-header forms ({@code localDeclNoSemi} and
 * {@code assignable}) exclude it because the header's clauses are separated by explicit
 * semicolons that are not part of any clause node.
 */
final class SolvikAstBuilder {

    private final SourceFile source;

    SolvikAstBuilder(SourceFile source) {
        this.source = source;
    }

    CompilationUnitNode build(CompilationUnitContext ctx) {
        List<AstNode> items = new ArrayList<>();
        ModuleDeclNode moduleDeclaration = null;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            Object child = ctx.getChild(i);
            if (child instanceof FunctionDeclContext fn) {
                items.add(buildFunction(fn));
            } else if (child instanceof ClassDeclContext cls) {
                items.add(buildClass(cls));
            } else if (child instanceof IncludeDeclContext include) {
                items.add(buildInclude(include));
            } else if (child instanceof ModuleDeclContext module) {
                moduleDeclaration = buildModule(module);
            } else if (child instanceof InterfaceDeclContext iface) {
                items.add(buildInterface(iface));
            } else if (child instanceof EnumDeclContext enumDecl) {
                items.add(buildEnum(enumDecl));
            } else if (child instanceof StatementContext statement) {
                items.add(buildStatement(statement));
            }
        }
        return new CompilationUnitNode(items, moduleDeclaration, span(ctx.getStart(), lastMeaningfulStop(ctx)));
    }

    private ModuleDeclNode buildModule(ModuleDeclContext ctx) {
        return new ModuleDeclNode(ctx.Identifier().getText(), span(ctx.getStart(), ctx.getStop()));
    }

    private IncludeDeclNode buildInclude(IncludeDeclContext ctx) {
        LiteralNode path;
        if (ctx.rawStringLiteral() != null) {
            RawStringLiteralContext raw = ctx.rawStringLiteral();
            path = new RawStringLiteralNode(raw.RAW_STRING_LITERAL().getText(), span(raw.getStart(), raw.getStop()));
        } else {
            StringLiteralContext literal = ctx.stringLiteral();
            path = new StringLiteralNode(literal.STRING_LITERAL().getText(), span(literal.getStart(), literal.getStop()));
        }
        String alias = ctx.Identifier() == null ? null : ctx.Identifier().getText();
        return new IncludeDeclNode(path, alias, span(ctx.getStart(), ctx.getStop()));
    }

    private ClassDeclNode buildClass(ClassDeclContext ctx) {
        boolean sealed = ctx.SEALED() != null;
        boolean open = ctx.OPEN() != null;
        TypeRefNode superClass = ctx.typeRef() == null ? null : buildTypeRef(ctx.typeRef());
        List<TypeRefNode> interfaces = new ArrayList<>();
        if (ctx.typeRefList() != null) {
            for (TypeRefContext type : ctx.typeRefList().typeRef()) {
                interfaces.add(buildTypeRef(type));
            }
        }
        List<AstNode> members = new ArrayList<>();
        for (ClassMemberContext member : ctx.classMember()) {
            if (member.propertyDecl() != null) {
                members.add(buildProperty(member.propertyDecl()));
            } else if (member.delegateDecl() != null) {
                members.add(buildDelegate(member.delegateDecl()));
            } else if (member.constructorDecl() != null) {
                members.add(buildConstructor(member.constructorDecl()));
            } else {
                members.add(buildMethod(member.methodDecl()));
            }
        }
        return new ClassDeclNode(sealed, open, ctx.Identifier().getText(), buildTypeParameters(ctx.typeParameterList()), superClass, interfaces, members, span(ctx.getStart(), ctx.getStop()));
    }

    private EnumDeclNode buildEnum(EnumDeclContext ctx) {
        List<EnumVariantNode> variants = new ArrayList<>();
        for (EnumVariantContext variant : ctx.enumVariant()) {
            List<TypeRefNode> valueTypes = new ArrayList<>();
            if (variant.typeRefList() != null) {
                for (TypeRefContext type : variant.typeRefList().typeRef()) {
                    valueTypes.add(buildTypeRef(type));
                }
            }
            variants.add(new EnumVariantNode(variant.Identifier().getText(), valueTypes, span(variant.getStart(), variant.getStop())));
        }
        return new EnumDeclNode(ctx.Identifier().getText(), buildTypeParameters(ctx.typeParameterList()), variants, span(ctx.getStart(), ctx.getStop()));
    }

    private DelegateDeclNode buildDelegate(DelegateDeclContext ctx) {
        ExpressionNode initializer = ctx.expression() == null ? null : buildExpression(ctx.expression());
        return new DelegateDeclNode(ctx.Identifier().getText(), buildTypeRef(ctx.typeRef()), initializer, span(ctx.getStart(), ctx.getStop()));
    }

    private InterfaceDeclNode buildInterface(InterfaceDeclContext ctx) {
        List<TypeRefNode> superInterfaces = new ArrayList<>();
        if (ctx.typeRefList() != null) {
            for (TypeRefContext type : ctx.typeRefList().typeRef()) {
                superInterfaces.add(buildTypeRef(type));
            }
        }
        List<SignatureDeclNode> signatures = new ArrayList<>();
        List<FunctionDeclNode> defaultMethods = new ArrayList<>();
        for (InterfaceMemberContext member : ctx.interfaceMember()) {
            if (member.signatureDecl() != null) {
                signatures.add(buildSignature(member.signatureDecl()));
            } else {
                defaultMethods.add(buildDefaultMethod(member.defaultMethodDecl()));
            }
        }
        return new InterfaceDeclNode(ctx.Identifier().getText(), buildTypeParameters(ctx.typeParameterList()), superInterfaces, signatures, defaultMethods, span(ctx.getStart(), ctx.getStop()));
    }

    private SignatureDeclNode buildSignature(SignatureDeclContext ctx) {
        SourceSpan span = span(ctx.getStart(), ctx.getStop());
        TypeRefNode returnType = ctx.typeRef() == null ? implicitUnitReturnType(span) : buildTypeRef(ctx.typeRef());
        return new SignatureDeclNode(ctx.Identifier().getText(), buildTypeParameters(ctx.typeParameterList()), buildParameters(ctx.parameterList()), returnType, span);
    }

    private FunctionDeclNode buildDefaultMethod(DefaultMethodDeclContext ctx) {
        // An interface member carries no modifiers: it is inherited by every implementor, and a
        // class implementing method needs none either.
        return buildFunction(false, false, ctx.Identifier().getText(), ctx.typeParameterList(), ctx.parameterList(), ctx.typeRef(), ctx.block(), span(ctx.getStart(), ctx.getStop()));
    }

    private PropertyDeclNode buildProperty(PropertyDeclContext ctx) {
        BindingKind kind = "val".equals(ctx.bindingKind().getText()) ? BindingKind.VAL : BindingKind.VAR;
        TypeRefNode declaredType = ctx.typeRef() == null ? null : buildTypeRef(ctx.typeRef());
        ExpressionNode initializer = ctx.expression() == null ? null : buildExpression(ctx.expression());
        return new PropertyDeclNode(kind, ctx.Identifier().getText(), declaredType, initializer, span(ctx.getStart(), ctx.getStop()));
    }

    private ConstructorDeclNode buildConstructor(ConstructorDeclContext ctx) {
        List<ParameterNode> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            for (ParameterContext p : ctx.parameterList().parameter()) {
                parameters.add(new ParameterNode(p.Identifier().getText(), buildTypeRef(p.typeRef()), span(p.getStart(), p.getStop())));
            }
        }
        return new ConstructorDeclNode(ctx.Identifier().getText(), parameters, buildBlock(ctx.block()), span(ctx.getStart(), ctx.getStop()));
    }

    private FunctionDeclNode buildFunction(FunctionDeclContext ctx) {
        return buildFunction(false, false, ctx.Identifier().getText(), ctx.typeParameterList(), ctx.parameterList(), ctx.typeRef(), ctx.block(), span(ctx.getStart(), ctx.getStop()));
    }

    private List<TypeParameterNode> buildTypeParameters(TypeParameterListContext ctx) {
        List<TypeParameterNode> parameters = new ArrayList<>();
        if (ctx != null) {
            for (TerminalNode identifier : ctx.Identifier()) {
                parameters.add(new TypeParameterNode(identifier.getText(), span(identifier.getSymbol(), identifier.getSymbol())));
            }
        }
        return parameters;
    }

    private List<ParameterNode> buildParameters(org.solvik.parser.generated.SolvikParser.ParameterListContext parameterList) {
        List<ParameterNode> parameters = new ArrayList<>();
        if (parameterList != null) {
            for (ParameterContext p : parameterList.parameter()) {
                parameters.add(new ParameterNode(p.Identifier().getText(), buildTypeRef(p.typeRef()), span(p.getStart(), p.getStop())));
            }
        }
        return parameters;
    }

    private FunctionDeclNode buildMethod(MethodDeclContext ctx) {
        boolean open = false;
        boolean override = false;
        for (MethodModifierContext modifier : ctx.methodModifier()) {
            if (modifier.OPEN() != null) {
                open = true;
            } else if (modifier.OVERRIDE() != null) {
                override = true;
            }
        }
        return buildFunction(open, override, ctx.Identifier().getText(), ctx.typeParameterList(), ctx.parameterList(), ctx.typeRef(), ctx.block(), span(ctx.getStart(), ctx.getStop()));
    }

    private FunctionDeclNode buildFunction(boolean open, boolean override, String name, TypeParameterListContext typeParameterList, org.solvik.parser.generated.SolvikParser.ParameterListContext parameterList, TypeRefContext returnTypeCtx,
                    BlockContext bodyCtx, SourceSpan span) {
        List<TypeParameterNode> typeParameters = buildTypeParameters(typeParameterList);
        List<ParameterNode> parameters = buildParameters(parameterList);
        TypeRefNode returnType = returnTypeCtx == null ? implicitUnitReturnType(span) : buildTypeRef(returnTypeCtx);
        BlockNode body = buildBlock(bodyCtx);
        return new FunctionDeclNode(open, override, name, typeParameters, parameters, returnType, body, span);
    }

    /**
     * The return type of a callable that declares none: {@code Unit} (docs/LANGUAGE_SPEC.md
     * section 6). Synthesizing the reference here keeps every callable's return type non-null, so
     * neither the semantic layer nor lowering needs a special case for an omitted return type. The
     * reference is attributed to the declaration because it has no written location of its own.
     */
    private static TypeRefNode implicitUnitReturnType(SourceSpan span) {
        return new TypeRefNode("Unit", List.of(), false, span);
    }

    private TypeRefNode buildTypeRef(TypeRefContext ctx) {
        List<TypeRefNode> arguments = new ArrayList<>();
        TypeArgumentsContext argumentsCtx = ctx.typeArguments();
        if (argumentsCtx != null) {
            for (TypeRefContext argument : argumentsCtx.typeRef()) {
                arguments.add(buildTypeRef(argument));
            }
        }
        boolean qualified = ctx.COLONCOLON() != null;
        String prefix = qualified ? ctx.Identifier(0).getText() : null;
        String name = qualified ? ctx.Identifier(1).getText() : ctx.Identifier(0).getText();
        return new TypeRefNode(prefix, name, arguments, ctx.QUESTION() != null, span(ctx.getStart(), ctx.getStop()));
    }

    private BlockNode buildBlock(BlockContext ctx) {
        List<StatementNode> statements = new ArrayList<>();
        for (StatementContext s : ctx.statement()) {
            statements.add(buildStatement(s));
        }
        return new BlockNode(statements, span(ctx.getStart(), ctx.getStop()));
    }

    private StatementNode buildStatement(StatementContext ctx) {
        if (ctx.localDecl() != null) {
            return buildLocalDecl(ctx.localDecl());
        }
        if (ctx.ifStmt() != null) {
            return buildIf(ctx.ifStmt());
        }
        if (ctx.whileStmt() != null) {
            return buildWhile(ctx.whileStmt());
        }
        if (ctx.forStmt() != null) {
            return buildFor(ctx.forStmt());
        }
        if (ctx.forInStmt() != null) {
            return buildForIn(ctx.forInStmt());
        }
        if (ctx.switchStmt() != null) {
            return buildSwitch(ctx.switchStmt());
        }
        if (ctx.block() != null) {
            return buildBlock(ctx.block());
        }
        if (ctx.breakStmt() != null) {
            BreakStmtContext b = ctx.breakStmt();
            return new BreakStmtNode(span(b.getStart(), b.getStop()));
        }
        if (ctx.continueStmt() != null) {
            ContinueStmtContext c = ctx.continueStmt();
            return new ContinueStmtNode(span(c.getStart(), c.getStop()));
        }
        if (ctx.returnStmt() != null) {
            ReturnStmtContext r = ctx.returnStmt();
            ExpressionNode value = r.expression() == null ? null : buildExpression(r.expression());
            return new ReturnStmtNode(value, span(r.getStart(), r.getStop()));
        }
        if (ctx.exprStmt() != null) {
            ExprStmtContext e = ctx.exprStmt();
            SourceSpan span = span(e.getStart(), e.getStop());
            if (e.ASSIGN() != null) {
                return new AssignStmtNode(buildExpression(e.expression(0)), buildExpression(e.expression(1)), span);
            }
            return new ExprStmtNode(buildExpression(e.expression(0)), span);
        }
        throw new IllegalStateException("unsupported statement alternative: " + ctx.getText());
    }

    private LocalDeclNode buildLocalDecl(LocalDeclContext ctx) {
        return buildLocalDecl(ctx.bindingKind(), ctx.Identifier(), ctx.typeRef(), ctx.expression(), span(ctx.getStart(), ctx.getStop()));
    }

    private LocalDeclNode buildLocalDeclNoSemi(LocalDeclNoSemiContext ctx) {
        return buildLocalDecl(ctx.bindingKind(), ctx.Identifier(), ctx.typeRef(), ctx.expression(), span(ctx.getStart(), ctx.getStop()));
    }

    private LocalDeclNode buildLocalDecl(org.solvik.parser.generated.SolvikParser.BindingKindContext kindCtx, TerminalNode identifier, TypeRefContext typeRefCtx, ExpressionContext initCtx, SourceSpan span) {
        BindingKind kind = "val".equals(kindCtx.getText()) ? BindingKind.VAL : BindingKind.VAR;
        TypeRefNode declaredType = typeRefCtx == null ? null : buildTypeRef(typeRefCtx);
        return new LocalDeclNode(kind, identifier.getText(), declaredType, buildExpression(initCtx), span);
    }

    private IfStmtNode buildIf(IfStmtContext ctx) {
        ExpressionNode condition = buildExpression(ctx.expression());
        BlockNode thenBlock = buildBlock(ctx.block());
        ElseBranchNode elseBranch = null;
        if (ctx.elseBranch() != null) {
            ElseBranchContext eb = ctx.elseBranch();
            if (eb.ifStmt() != null) {
                elseBranch = ElseBranchNode.ofChainedIf(buildIf(eb.ifStmt()), span(eb.getStart(), eb.getStop()));
            } else {
                elseBranch = ElseBranchNode.ofBlock(buildBlock(eb.block()), span(eb.getStart(), eb.getStop()));
            }
        }
        return new IfStmtNode(condition, thenBlock, elseBranch, span(ctx.getStart(), ctx.getStop()));
    }

    private WhileStmtNode buildWhile(WhileStmtContext ctx) {
        return new WhileStmtNode(buildExpression(ctx.expression()), buildBlock(ctx.block()), span(ctx.getStart(), ctx.getStop()));
    }

    private ForStmtNode buildFor(ForStmtContext ctx) {
        StatementNode initializer = null;
        ForInitContext fi = ctx.forInit();
        if (fi != null) {
            initializer = fi.localDeclNoSemi() != null ? buildLocalDeclNoSemi(fi.localDeclNoSemi()) : buildAssignable(fi.assignable());
        }
        ExpressionNode condition = ctx.forCondition() == null ? null : buildExpression(ctx.forCondition().expression());
        StatementNode update = ctx.forUpdate() == null ? null : buildAssignable(ctx.forUpdate().assignable());
        BlockNode body = buildBlock(ctx.block());
        return new ForStmtNode(initializer, condition, update, body, span(ctx.getStart(), ctx.getStop()));
    }

    /** Builds a range for-in loop; the semantic layer declares the loop variable. */
    private ForInStmtNode buildForIn(ForInStmtContext ctx) {
        RangeOperator operator = RangeOperator.fromSpelling(ctx.rangeExpr().rangeOperator().getText());
        RangeExprContext range = ctx.rangeExpr();
        BlockNode body = buildBlock(ctx.block());
        return new ForInStmtNode(ctx.Identifier().getText(), operator, buildExpression(range.expression(0)), buildExpression(range.expression(1)), body, span(ctx.getStart(), ctx.getStop()));
    }

    /** Builds a {@code for}-clause assignment or bare expression; the semantic pass validates it. */
    private StatementNode buildAssignable(AssignableContext ctx) {
        SourceSpan span = span(ctx.getStart(), ctx.getStop());
        if (ctx.ASSIGN() != null) {
            return new AssignStmtNode(buildExpression(ctx.expression(0)), buildExpression(ctx.expression(1)), span);
        }
        return new ExprStmtNode(buildExpression(ctx.expression(0)), span);
    }

    /**
     * Builds a non-fallthrough {@code switch}. The generated context keeps the cases in source
     * order, so iterating its children preserves the specification's first-match order; the
     * semantic pass enforces "at most one default, and last".
     */
    private SwitchStmtNode buildSwitch(SwitchStmtContext ctx) {
        ExpressionNode scrutinee = buildExpression(ctx.expression());
        List<SwitchCaseNode> cases = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            Object child = ctx.getChild(i);
            if (child instanceof SwitchCaseContext switchCase) {
                cases.add(buildSwitchCase(switchCase));
            } else if (child instanceof DefaultCaseContext defaultCase) {
                cases.add(buildDefaultCase(defaultCase));
            }
        }
        return new SwitchStmtNode(scrutinee, cases, span(ctx.getStart(), ctx.getStop()));
    }

    /**
     * Builds a {@code switch} expression (docs/LANGUAGE_SPEC.md section 21). The surface syntax is
     * shared with the statement form; every case body is built as a value-required body so a
     * terminal expression becomes the case result.
     */
    private SwitchExprNode buildSwitchExpr(SwitchExprContext ctx) {
        ExpressionNode scrutinee = buildExpression(ctx.expression());
        List<SwitchCaseNode> cases = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            Object child = ctx.getChild(i);
            if (child instanceof ValueSwitchCaseContext switchCase) {
                cases.add(buildValueSwitchCase(switchCase));
            } else if (child instanceof ValueDefaultCaseContext defaultCase) {
                cases.add(buildValueDefaultCase(defaultCase));
            }
        }
        return new SwitchExprNode(scrutinee, cases, span(ctx.getStart(), ctx.getStop()));
    }

    private SwitchCaseNode buildSwitchCase(SwitchCaseContext ctx) {
        List<CaseLabelNode> labels = new ArrayList<>();
        for (CaseLabelContext label : ctx.caseLabel()) {
            labels.add(buildCaseLabel(label));
        }
        return new SwitchCaseNode(false, labels, buildCaseBody(ctx.statement(), ctx.COLON().getSymbol()), span(ctx.getStart(), ctx.getStop()));
    }

    private SwitchCaseNode buildDefaultCase(DefaultCaseContext ctx) {
        return new SwitchCaseNode(true, List.of(), buildCaseBody(ctx.statement(), ctx.COLON().getSymbol()), span(ctx.getStart(), ctx.getStop()));
    }

    private SwitchCaseNode buildValueSwitchCase(ValueSwitchCaseContext ctx) {
        List<CaseLabelNode> labels = new ArrayList<>();
        for (CaseLabelContext label : ctx.caseLabel()) {
            labels.add(buildCaseLabel(label));
        }
        return new SwitchCaseNode(false, labels, buildValueCaseBody(ctx.valueCaseBody(), ctx.COLON().getSymbol()), span(ctx.getStart(), ctx.getStop()));
    }

    private SwitchCaseNode buildValueDefaultCase(ValueDefaultCaseContext ctx) {
        return new SwitchCaseNode(true, List.of(), buildValueCaseBody(ctx.valueCaseBody(), ctx.COLON().getSymbol()), span(ctx.getStart(), ctx.getStop()));
    }

    /**
     * Builds the implicit block forming a statement case body. The body has no braces, so its span
     * runs from the first statement to the last; an empty body is anchored just after its colon.
     */
    private BlockNode buildCaseBody(List<StatementContext> statements, Token colon) {
        List<StatementNode> built = new ArrayList<>();
        for (StatementContext statement : statements) {
            built.add(buildStatement(statement));
        }
        return new BlockNode(built, caseBodySpan(built, colon));
    }

    /** Builds the value-required implicit block forming an expression {@code switch} case body. */
    private BlockNode buildValueCaseBody(ValueCaseBodyContext ctx, Token colon) {
        List<StatementNode> built = new ArrayList<>();
        for (StatementContext statement : ctx.statement()) {
            built.add(buildStatement(statement));
        }
        ValueTailContext tail = ctx.valueTail();
        if (tail != null) {
            // The terminal expression is part of the body, so the body span must reach its end: a
            // body of only a tail expression would otherwise have an empty span that does not
            // contain its own child. A body that also has statements starts at the first of them.
            ExpressionNode tailExpression = buildExpression(tail.expression());
            SourceSpan statementsSpan = caseBodySpan(built, colon);
            return new BlockNode(built, tailExpression, sourceSpan(statementsSpan.startOffset(), tailExpression.span().endOffset()));
        }
        return valueBlock(built, caseBodySpan(built, colon));
    }

    private SourceSpan caseBodySpan(List<StatementNode> built, Token colon) {
        if (built.isEmpty()) {
            int offset = colon.getStopIndex() + 1;
            return sourceSpan(offset, offset);
        }
        return sourceSpan(built.get(0).span().startOffset(), built.get(built.size() - 1).span().endOffset());
    }

    private CaseLabelNode buildCaseLabel(CaseLabelContext ctx) {
        if (ctx.regexCaseLabel() != null) {
            return buildRegexCaseLabel(ctx.regexCaseLabel());
        }
        return new ConstantCaseLabelNode(buildExpression(ctx.expression()), span(ctx.getStart(), ctx.getStop()));
    }

    private RegexCaseLabelNode buildRegexCaseLabel(RegexCaseLabelContext ctx) {
        ExpressionNode pattern;
        if (ctx.rawStringLiteral() != null) {
            RawStringLiteralContext raw = ctx.rawStringLiteral();
            pattern = new RawStringLiteralNode(raw.RAW_STRING_LITERAL().getText(), span(raw.getStart(), raw.getStop()));
        } else {
            StringLiteralContext literal = ctx.stringLiteral();
            pattern = new StringLiteralNode(literal.STRING_LITERAL().getText(), span(literal.getStart(), literal.getStop()));
        }
        return new RegexCaseLabelNode(pattern, span(ctx.getStart(), ctx.getStop()));
    }

    private ExpressionNode buildExpression(ExpressionContext ctx) {
        return buildNullCoalescing(ctx.nullCoalescing());
    }

    /**
     * Folds the lowest-precedence {@code ??} chain left to right. The operands are already-complete
     * {@code logicalOr} nodes, so null coalescing is the outermost operator of every expression.
     */
    private ExpressionNode buildNullCoalescing(NullCoalescingContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (LogicalOrContext child : ctx.logicalOr()) {
            operands.add(buildLogicalOr(child));
        }
        return fold(operands, ctx);
    }

    /** Children alternate: operand (operator operand)*. Operators are terminals at odd indices. */
    private ExpressionNode fold(List<ExpressionNode> operands, ParserRuleContext ctx) {
        ExpressionNode result = operands.get(0);
        int operand = 1;
        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            BinaryOperator op = BinaryOperator.fromSpelling(operatorText(ctx, i));
            ExpressionNode rhs = operands.get(operand++);
            result = new BinaryExprNode(op, result, rhs, sourceSpan(result.span().startOffset(), rhs.span().endOffset()));
        }
        return result;
    }

    private ExpressionNode buildLogicalOr(LogicalOrContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (LogicalAndContext child : ctx.logicalAnd()) {
            operands.add(buildLogicalAnd(child));
        }
        return fold(operands, ctx);
    }

    private ExpressionNode buildLogicalAnd(LogicalAndContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (EqualityContext child : ctx.equality()) {
            operands.add(buildEquality(child));
        }
        return fold(operands, ctx);
    }

    private ExpressionNode buildEquality(EqualityContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (RelationalContext child : ctx.relational()) {
            operands.add(buildRelational(child));
        }
        return fold(operands, ctx);
    }

    private ExpressionNode buildRelational(RelationalContext ctx) {
        ExpressionNode expr = buildConcat(ctx.concat());
        for (RelationContext relation : ctx.relation()) {
            if (relation.IS() != null) {
                TypeRefNode target = buildTypeRef(relation.typeRef());
                expr = new TypeTestExprNode(expr, target, sourceSpan(expr.span().startOffset(), target.span().endOffset()));
            } else if (relation.AS() != null) {
                TypeRefNode target = buildTypeRef(relation.typeRef());
                expr = new CastExprNode(expr, target, sourceSpan(expr.span().startOffset(), target.span().endOffset()));
            } else {
                ExpressionNode rhs = buildConcat(relation.concat());
                BinaryOperator op = BinaryOperator.fromSpelling(operatorText(relation, 0));
                expr = new BinaryExprNode(op, expr, rhs, sourceSpan(expr.span().startOffset(), rhs.span().endOffset()));
            }
        }
        return expr;
    }

    private ExpressionNode buildConcat(ConcatContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (AdditiveContext child : ctx.additive()) {
            operands.add(buildAdditive(child));
        }
        return fold(operands, ctx);
    }

    private ExpressionNode buildAdditive(AdditiveContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (MultiplicativeContext child : ctx.multiplicative()) {
            operands.add(buildMultiplicative(child));
        }
        return fold(operands, ctx);
    }

    private ExpressionNode buildMultiplicative(MultiplicativeContext ctx) {
        List<ExpressionNode> operands = new ArrayList<>();
        for (UnaryContext child : ctx.unary()) {
            operands.add(buildUnary(child));
        }
        return fold(operands, ctx);
    }

    private static String operatorText(ParserRuleContext ctx, int childIndex) {
        return ((TerminalNode) ctx.getChild(childIndex)).getText();
    }

    /**
     * A prefix unary expression consumes a terminal and a nested {@code unary}; a plain unary
     * expression is its postfix operand. Unary binds looser than calls/member access but tighter
     * than every binary operator, matching the specification precedence table.
     */
    private ExpressionNode buildUnary(UnaryContext ctx) {
        if (ctx.unary() != null) {
            ExpressionNode operand = buildUnary(ctx.unary());
            UnaryOperator op = ctx.BANG() != null ? UnaryOperator.NOT : UnaryOperator.NEGATE;
            return new UnaryExprNode(op, operand, sourceSpan(ctx.getStart().getStartIndex(), operand.span().endOffset()));
        }
        return buildPostfix(ctx.postfix());
    }

    /**
     * Folds {@code primary suffix*} left to right, so {@code f(1).g(2)} becomes
     * {@code Call(Member(Call(f,[1]), g), [2])}; each folded node starts at the base primary and
     * ends at its own suffix.
     */
    private ExpressionNode buildPostfix(PostfixContext ctx) {
        ExpressionNode expr = buildPrimary(ctx.primary());
        int baseStart = expr.span().startOffset();
        for (SuffixContext s : ctx.suffix()) {
            if (s.memberSuffix() != null) {
                MemberSuffixContext m = s.memberSuffix();
                boolean safe = m.NULLABLE_DOT() != null;
                expr = new MemberAccessExprNode(expr, m.Identifier().getText(), safe, sourceSpan(baseStart, m.getStop().getStopIndex() + 1));
            } else if (s.namespaceSuffix() != null) {
                NamespaceSuffixContext n = s.namespaceSuffix();
                expr = new NamespaceAccessExprNode(expr, n.Identifier().getText(), sourceSpan(baseStart, n.getStop().getStopIndex() + 1));
            } else {
                CallSuffixContext c = s.callSuffix();
                List<TypeRefNode> typeArguments = new ArrayList<>();
                TypeArgumentsContext typeArgumentsContext = c.typeArguments();
                if (typeArgumentsContext != null) {
                    for (TypeRefContext argument : typeArgumentsContext.typeRef()) {
                        typeArguments.add(buildTypeRef(argument));
                    }
                }
                List<ExpressionNode> args = new ArrayList<>();
                ArgumentListContext al = c.argumentList();
                if (al != null) {
                    for (CallArgumentContext a : al.callArgument()) {
                        ExpressionContext key = a.expression(0);
                        if (a.COLON() != null) {
                            ExpressionNode keyExpr = buildExpression(key);
                            ExpressionNode valueExpr = buildExpression(a.expression(1));
                            args.add(new MapEntryExprNode(keyExpr, valueExpr, sourceSpan(key.getStart().getStartIndex(), a.expression(1).getStop().getStopIndex() + 1)));
                        } else {
                            args.add(buildExpression(key));
                        }
                    }
                }
                Token stop = c.getStop();
                int end = stop.getStopIndex() >= 0 ? stop.getStopIndex() + 1 : c.getStart().getStartIndex() + 1;
                expr = new CallExprNode(expr, typeArguments, args, sourceSpan(baseStart, end));
            }
        }
        return expr;
    }

    private ExpressionNode buildPrimary(PrimaryContext ctx) {
        ExpressionNode expr;
        if (ctx.literal() != null) {
            expr = buildLiteral(ctx.literal());
        } else if (ctx.paren() != null) {
            ParenContext p = ctx.paren();
            expr = new ParenExprNode(buildExpression(p.expression()), span(p.getStart(), p.getStop()));
        } else if (ctx.thisExpr() != null) {
            ThisExprContext t = ctx.thisExpr();
            expr = new ThisExprNode(span(t.getStart(), t.getStop()));
        } else if (ctx.superExpr() != null) {
            SuperExprContext s = ctx.superExpr();
            expr = new SuperExprNode(span(s.getStart(), s.getStop()));
        } else if (ctx.matchExpr() != null) {
            expr = buildMatch(ctx.matchExpr());
        } else if (ctx.ifExpr() != null) {
            expr = buildIfExpr(ctx.ifExpr());
        } else if (ctx.switchExpr() != null) {
            expr = buildSwitchExpr(ctx.switchExpr());
        } else if (ctx.blockExpr() != null) {
            BlockExprContext blockExpr = ctx.blockExpr();
            expr = new BlockExprNode(buildValueBlock(blockExpr.valueBlock()), span(blockExpr.getStart(), blockExpr.getStop()));
        } else {
            NameContext n = ctx.name();
            expr = new NameRefExprNode(n.Identifier().getText(), span(n.getStart(), n.getStop()));
        }
        return expr;
    }

    /**
     * Builds an expression {@code if} (docs/LANGUAGE_SPEC.md section 21). The then part and a braced
     * {@code else} part are value-required blocks; a chained {@code else if} is a nested expression.
     */
    private IfExprNode buildIfExpr(IfExprContext ctx) {
        ExpressionNode condition = buildExpression(ctx.expression());
        BlockNode thenBlock = buildValueBlock(ctx.valueBlock());
        ExpressionNode elseValue = null;
        ElseExprBranchContext elseBranch = ctx.elseExprBranch();
        if (elseBranch != null) {
            if (elseBranch.ifExpr() != null) {
                elseValue = buildIfExpr(elseBranch.ifExpr());
            } else {
                ValueBlockContext elseBlock = elseBranch.valueBlock();
                elseValue = new BlockExprNode(buildValueBlock(elseBlock), span(elseBlock.getStart(), elseBlock.getStop()));
            }
        }
        return new IfExprNode(condition, thenBlock, elseValue, span(ctx.getStart(), ctx.getStop()));
    }

    /** Builds a value-required block from a braced body, honoring an explicit terminal expression. */
    private BlockNode buildValueBlock(ValueBlockContext ctx) {
        List<StatementNode> statements = new ArrayList<>();
        for (StatementContext s : ctx.statement()) {
            statements.add(buildStatement(s));
        }
        SourceSpan span = span(ctx.getStart(), ctx.getStop());
        ValueTailContext tail = ctx.valueTail();
        if (tail != null) {
            return new BlockNode(statements, buildExpression(tail.expression()), span);
        }
        return valueBlock(statements, span);
    }

    /**
     * Reinterprets an already-built statement block as value-required. The final item is moved into
     * the block's tail when it is expression-form: an expression statement, or an {@code if} or
     * {@code switch} statement that becomes the corresponding expression. Explicit and synthesized
     * terminal semicolons are indistinguishable because both produced the same expression-statement
     * node, so no token origin is inspected.
     */
    private BlockNode valueBlockFrom(BlockNode block) {
        return valueBlock(new ArrayList<>(block.statements()), block.span());
    }

    private BlockNode valueBlock(List<StatementNode> statements, SourceSpan span) {
        if (statements.isEmpty()) {
            return new BlockNode(List.of(), null, span);
        }
        StatementNode last = statements.get(statements.size() - 1);
        ExpressionNode tail;
        if (last instanceof ExprStmtNode exprStmt) {
            tail = exprStmt.expression();
        } else if (last instanceof IfStmtNode ifStmt) {
            tail = toIfExpr(ifStmt);
        } else if (last instanceof SwitchStmtNode switchStmt) {
            tail = toSwitchExpr(switchStmt);
        } else {
            return new BlockNode(statements, null, span);
        }
        List<StatementNode> prefix = new ArrayList<>(statements.subList(0, statements.size() - 1));
        return new BlockNode(prefix, tail, span);
    }

    /** Converts a trailing statement {@code if} into an expression form within a value body. */
    private IfExprNode toIfExpr(IfStmtNode statement) {
        BlockNode thenBlock = valueBlockFrom(statement.thenBlock());
        ExpressionNode elseValue = statement.elseBranch().map(this::toElseExpr).orElse(null);
        return new IfExprNode(statement.condition(), thenBlock, elseValue, statement.span());
    }

    private ExpressionNode toElseExpr(ElseBranchNode branch) {
        if (branch.isChainedIf()) {
            return toIfExpr(branch.chainedIf().get());
        }
        BlockNode block = branch.block().get();
        return new BlockExprNode(valueBlockFrom(block), block.span());
    }

    /** Converts a trailing statement {@code switch} into an expression form within a value body. */
    private SwitchExprNode toSwitchExpr(SwitchStmtNode statement) {
        List<SwitchCaseNode> cases = new ArrayList<>();
        for (SwitchCaseNode switchCase : statement.cases()) {
            cases.add(new SwitchCaseNode(switchCase.isDefault(), switchCase.labels(), valueBlockFrom(switchCase.body()), switchCase.span()));
        }
        return new SwitchExprNode(statement.scrutinee(), cases, statement.span());
    }

    /**
     * Builds a {@code match} expression. The scrutinee is an ordinary expression and each branch is
     * a pattern paired with its result; the grammar has already consumed branch separators.
     */
    private MatchExprNode buildMatch(MatchExprContext ctx) {
        ExpressionNode scrutinee = buildExpression(ctx.expression());
        List<MatchBranchNode> branches = new ArrayList<>();
        for (MatchBranchContext branch : ctx.matchBranch()) {
            branches.add(buildMatchBranch(branch));
        }
        return new MatchExprNode(scrutinee, branches, span(ctx.getStart(), ctx.getStop()));
    }

    private MatchBranchNode buildMatchBranch(MatchBranchContext ctx) {
        PatternNode pattern = buildPattern(ctx.pattern(), true);
        ExpressionNode result = buildExpression(ctx.expression());
        return new MatchBranchNode(pattern, result, span(ctx.getStart(), ctx.getStop()));
    }

    /**
     * Builds one pattern. A bare name is a value-less enum variant at the top level of a branch and
     * a binding inside a variant's argument list, so the caller passes the context down. The colon
     * form is always a binding and the wildcard is the bare name {@code _}.
     */
    private PatternNode buildPattern(PatternContext ctx, boolean topLevel) {
        String name = ctx.Identifier().getText();
        SourceSpan span = span(ctx.getStart(), ctx.getStop());
        if (ctx.COLON() != null) {
            return new BindingPatternNode(name, buildTypeRef(ctx.typeRef()), span);
        }
        if (ctx.LPAREN() != null) {
            List<PatternNode> arguments = new ArrayList<>();
            PatternListContext list = ctx.patternList();
            if (list != null) {
                for (PatternContext argument : list.pattern()) {
                    arguments.add(buildPattern(argument, false));
                }
            }
            return new EnumPatternNode(name, arguments, span);
        }
        if ("_".equals(name)) {
            return new WildcardPatternNode(span);
        }
        if (topLevel) {
            return new EnumPatternNode(name, List.of(), span);
        }
        return new BindingPatternNode(name, null, span);
    }

    private ExpressionNode buildLiteral(org.solvik.parser.generated.SolvikParser.LiteralContext ctx) {
        if (ctx.integerLiteral() != null) {
            IntegerLiteralContext l = ctx.integerLiteral();
            return new IntegerLiteralNode(l.INTEGER_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.longLiteral() != null) {
            LongLiteralContext l = ctx.longLiteral();
            return new LongLiteralNode(l.LONG_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.floatingLiteral() != null) {
            FloatingLiteralContext l = ctx.floatingLiteral();
            return new FloatingLiteralNode(l.FLOATING_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.boolLiteral() != null) {
            BoolLiteralContext l = ctx.boolLiteral();
            return new BoolLiteralNode(l.BOOL_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.characterLiteral() != null) {
            CharacterLiteralContext l = ctx.characterLiteral();
            return new CharacterLiteralNode(l.CHARACTER_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.stringLiteral() != null) {
            StringLiteralContext l = ctx.stringLiteral();
            return new StringLiteralNode(l.STRING_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.nullLiteral() != null) {
            NullLiteralContext l = ctx.nullLiteral();
            return new NullLiteralNode(span(l.getStart(), l.getStop()));
        }
        RawStringLiteralContext l = ctx.rawStringLiteral();
        return new RawStringLiteralNode(l.RAW_STRING_LITERAL().getText(), span(l.getStart(), l.getStop()));
    }

    /**
     * End offset of a compilation-unit context excluding the virtual EOF token position.
     *
     * <p>The rule always ends in EOF, so the context's own stop token is never the answer. The end
     * is taken from the last child the rule actually consumed rather than from an enumeration of
     * declaration alternatives, so a construct added to {@code compilationUnit} later (top-level
     * statements and stand-alone semicolons are already such members) is included automatically and
     * the unit span can never truncate the items it reports.
     */
    private static Token lastMeaningfulStop(CompilationUnitContext ctx) {
        Token last = null;
        for (ParseTree child : ctx.children) {
            Token stop = stopOf(child);
            if (stop == null || stop.getType() == Token.EOF) {
                continue;
            }
            if (last == null || stop.getStopIndex() > last.getStopIndex()) {
                last = stop;
            }
        }
        return last == null ? ctx.getStart() : last;
    }

    /** The stop token of a rule child, whether that child is a nested rule or a consumed token. */
    private static Token stopOf(ParseTree child) {
        if (child instanceof ParserRuleContext context) {
            return context.getStop();
        }
        if (child instanceof TerminalNode terminal) {
            return terminal.getSymbol();
        }
        return null;
    }

    private SourceSpan sourceSpan(int startOffset, int endOffset) {
        return SourceSpan.of(source.id(), startOffset, endOffset);
    }

    private SourceSpan span(Token start, Token stop) {
        int first = Math.max(start.getStartIndex(), 0);
        int end = stop == null ? first : stop.getStopIndex() + 1;
        if (end < first) {
            end = first;
        }
        return sourceSpan(first, end);
    }
}
