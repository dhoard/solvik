/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.parser;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.InitDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.CharLiteralNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.FloatingLiteralNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.LongLiteralNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.expression.SuperExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.ast.expression.UnaryOperator;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BindingKind;
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
import org.solvik.parser.generated.SolvikParser.AdditiveContext;
import org.solvik.parser.generated.SolvikParser.ArgumentListContext;
import org.solvik.parser.generated.SolvikParser.AssignableContext;
import org.solvik.parser.generated.SolvikParser.BlockContext;
import org.solvik.parser.generated.SolvikParser.BoolLiteralContext;
import org.solvik.parser.generated.SolvikParser.BreakStmtContext;
import org.solvik.parser.generated.SolvikParser.CallSuffixContext;
import org.solvik.parser.generated.SolvikParser.CharLiteralContext;
import org.solvik.parser.generated.SolvikParser.ClassDeclContext;
import org.solvik.parser.generated.SolvikParser.ClassMemberContext;
import org.solvik.parser.generated.SolvikParser.CompilationUnitContext;
import org.solvik.parser.generated.SolvikParser.ContinueStmtContext;
import org.solvik.parser.generated.SolvikParser.ElseBranchContext;
import org.solvik.parser.generated.SolvikParser.DefaultMethodDeclContext;
import org.solvik.parser.generated.SolvikParser.EqualityContext;
import org.solvik.parser.generated.SolvikParser.ExprStmtContext;
import org.solvik.parser.generated.SolvikParser.ExpressionContext;
import org.solvik.parser.generated.SolvikParser.FloatingLiteralContext;
import org.solvik.parser.generated.SolvikParser.ForInitContext;
import org.solvik.parser.generated.SolvikParser.ForStmtContext;
import org.solvik.parser.generated.SolvikParser.FunctionDeclContext;
import org.solvik.parser.generated.SolvikParser.IfStmtContext;
import org.solvik.parser.generated.SolvikParser.InitDeclContext;
import org.solvik.parser.generated.SolvikParser.InterfaceDeclContext;
import org.solvik.parser.generated.SolvikParser.InterfaceMemberContext;
import org.solvik.parser.generated.SolvikParser.IntLiteralContext;
import org.solvik.parser.generated.SolvikParser.LocalDeclContext;
import org.solvik.parser.generated.SolvikParser.LocalDeclNoSemiContext;
import org.solvik.parser.generated.SolvikParser.LogicalAndContext;
import org.solvik.parser.generated.SolvikParser.LogicalOrContext;
import org.solvik.parser.generated.SolvikParser.LongLiteralContext;
import org.solvik.parser.generated.SolvikParser.MemberSuffixContext;
import org.solvik.parser.generated.SolvikParser.MethodDeclContext;
import org.solvik.parser.generated.SolvikParser.MethodModifierContext;
import org.solvik.parser.generated.SolvikParser.MultiplicativeContext;
import org.solvik.parser.generated.SolvikParser.NameContext;
import org.solvik.parser.generated.SolvikParser.ParameterContext;
import org.solvik.parser.generated.SolvikParser.ParenContext;
import org.solvik.parser.generated.SolvikParser.PostfixContext;
import org.solvik.parser.generated.SolvikParser.PrimaryContext;
import org.solvik.parser.generated.SolvikParser.PropertyDeclContext;
import org.solvik.parser.generated.SolvikParser.RawStringLiteralContext;
import org.solvik.parser.generated.SolvikParser.RelationalContext;
import org.solvik.parser.generated.SolvikParser.ReturnStmtContext;
import org.solvik.parser.generated.SolvikParser.SignatureDeclContext;
import org.solvik.parser.generated.SolvikParser.StatementContext;
import org.solvik.parser.generated.SolvikParser.StringLiteralContext;
import org.solvik.parser.generated.SolvikParser.SuffixContext;
import org.solvik.parser.generated.SolvikParser.SuperExprContext;
import org.solvik.parser.generated.SolvikParser.ThisExprContext;
import org.solvik.parser.generated.SolvikParser.TypeRefContext;
import org.solvik.parser.generated.SolvikParser.UnaryContext;
import org.solvik.parser.generated.SolvikParser.WhileStmtContext;
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

    SolvikAstBuilder() {
    }

    CompilationUnitNode build(CompilationUnitContext ctx) {
        List<DeclarationNode> declarations = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            Object child = ctx.getChild(i);
            if (child instanceof FunctionDeclContext fn) {
                declarations.add(buildFunction(fn));
            } else if (child instanceof ClassDeclContext cls) {
                declarations.add(buildClass(cls));
            } else if (child instanceof InterfaceDeclContext iface) {
                declarations.add(buildInterface(iface));
            }
        }
        return new CompilationUnitNode(declarations, span(ctx.getStart(), lastMeaningfulStop(ctx)));
    }

    private ClassDeclNode buildClass(ClassDeclContext ctx) {
        boolean open = ctx.OPEN() != null;
        TypeRefNode superClass = ctx.typeRef() == null ? null : buildTypeRef(ctx.typeRef());
        List<TypeRefNode> interfaces = new ArrayList<>();
        if (ctx.typeRefList() != null) {
            for (TypeRefContext type : ctx.typeRefList().typeRef()) {
                interfaces.add(buildTypeRef(type));
            }
        }
        List<PropertyDeclNode> properties = new ArrayList<>();
        List<FunctionDeclNode> methods = new ArrayList<>();
        List<InitDeclNode> initializers = new ArrayList<>();
        for (ClassMemberContext member : ctx.classMember()) {
            if (member.propertyDecl() != null) {
                properties.add(buildProperty(member.propertyDecl()));
            } else if (member.initDecl() != null) {
                initializers.add(buildInit(member.initDecl()));
            } else {
                methods.add(buildMethod(member.methodDecl()));
            }
        }
        return new ClassDeclNode(open, ctx.Identifier().getText(), superClass, interfaces, properties, initializers, methods, span(ctx.getStart(), ctx.getStop()));
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
        return new InterfaceDeclNode(ctx.Identifier().getText(), superInterfaces, signatures, defaultMethods, span(ctx.getStart(), ctx.getStop()));
    }

    private SignatureDeclNode buildSignature(SignatureDeclContext ctx) {
        return new SignatureDeclNode(ctx.Identifier().getText(), buildParameters(ctx.parameterList()), buildTypeRef(ctx.typeRef()), span(ctx.getStart(), ctx.getStop()));
    }

    private FunctionDeclNode buildDefaultMethod(DefaultMethodDeclContext ctx) {
        // An interface member carries no modifiers: it is inherited by every implementor, and a
        // class implementing method needs none either.
        return buildFunction(false, false, ctx.Identifier().getText(), ctx.parameterList(), ctx.typeRef(), ctx.block(), span(ctx.getStart(), ctx.getStop()));
    }

    private PropertyDeclNode buildProperty(PropertyDeclContext ctx) {
        BindingKind kind = "val".equals(ctx.bindingKind().getText()) ? BindingKind.VAL : BindingKind.VAR;
        TypeRefNode declaredType = ctx.typeRef() == null ? null : buildTypeRef(ctx.typeRef());
        ExpressionNode initializer = ctx.expression() == null ? null : buildExpression(ctx.expression());
        return new PropertyDeclNode(kind, ctx.Identifier().getText(), declaredType, initializer, span(ctx.getStart(), ctx.getStop()));
    }

    private InitDeclNode buildInit(InitDeclContext ctx) {
        List<ParameterNode> parameters = new ArrayList<>();
        if (ctx.parameterList() != null) {
            for (ParameterContext p : ctx.parameterList().parameter()) {
                parameters.add(new ParameterNode(p.Identifier().getText(), buildTypeRef(p.typeRef()), span(p.getStart(), p.getStop())));
            }
        }
        return new InitDeclNode(parameters, buildBlock(ctx.block()), span(ctx.getStart(), ctx.getStop()));
    }

    private FunctionDeclNode buildFunction(FunctionDeclContext ctx) {
        return buildFunction(false, false, ctx.Identifier().getText(), ctx.parameterList(), ctx.typeRef(), ctx.block(), span(ctx.getStart(), ctx.getStop()));
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
        return buildFunction(open, override, ctx.Identifier().getText(), ctx.parameterList(), ctx.typeRef(), ctx.block(), span(ctx.getStart(), ctx.getStop()));
    }

    private FunctionDeclNode buildFunction(boolean open, boolean override, String name, org.solvik.parser.generated.SolvikParser.ParameterListContext parameterList, TypeRefContext returnTypeCtx,
                    BlockContext bodyCtx, SourceSpan span) {
        List<ParameterNode> parameters = buildParameters(parameterList);
        TypeRefNode returnType = buildTypeRef(returnTypeCtx);
        BlockNode body = buildBlock(bodyCtx);
        return new FunctionDeclNode(open, override, name, parameters, returnType, body, span);
    }

    private TypeRefNode buildTypeRef(TypeRefContext ctx) {
        return new TypeRefNode(ctx.Identifier().getText(), span(ctx.getStart(), ctx.getStop()));
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

    /** Builds a {@code for}-clause assignment or bare expression; the semantic pass validates it. */
    private StatementNode buildAssignable(AssignableContext ctx) {
        SourceSpan span = span(ctx.getStart(), ctx.getStop());
        if (ctx.ASSIGN() != null) {
            return new AssignStmtNode(buildExpression(ctx.expression(0)), buildExpression(ctx.expression(1)), span);
        }
        return new ExprStmtNode(buildExpression(ctx.expression(0)), span);
    }

    private ExpressionNode buildExpression(ExpressionContext ctx) {
        return buildLogicalOr(ctx.logicalOr());
    }

    /** Children alternate: operand (operator operand)*. Operators are terminals at odd indices. */
    private ExpressionNode fold(List<ExpressionNode> operands, ParserRuleContext ctx) {
        ExpressionNode result = operands.get(0);
        int operand = 1;
        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            BinaryOperator op = BinaryOperator.fromSpelling(operatorText(ctx, i));
            ExpressionNode rhs = operands.get(operand++);
            result = new BinaryExprNode(op, result, rhs, SourceSpan.of(result.span().startOffset(), rhs.span().endOffset()));
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
            return new UnaryExprNode(op, operand, SourceSpan.of(ctx.getStart().getStartIndex(), operand.span().endOffset()));
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
                expr = new MemberAccessExprNode(expr, m.Identifier().getText(), SourceSpan.of(baseStart, m.getStop().getStopIndex() + 1));
            } else {
                CallSuffixContext c = s.callSuffix();
                List<ExpressionNode> args = new ArrayList<>();
                ArgumentListContext al = c.argumentList();
                if (al != null) {
                    for (ExpressionContext a : al.expression()) {
                        args.add(buildExpression(a));
                    }
                }
                Token stop = c.getStop();
                int end = stop.getStopIndex() >= 0 ? stop.getStopIndex() + 1 : c.getStart().getStartIndex() + 1;
                expr = new CallExprNode(expr, args, SourceSpan.of(baseStart, end));
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
        } else {
            NameContext n = ctx.name();
            expr = new NameRefExprNode(n.Identifier().getText(), span(n.getStart(), n.getStop()));
        }
        return expr;
    }

    private ExpressionNode buildLiteral(org.solvik.parser.generated.SolvikParser.LiteralContext ctx) {
        if (ctx.intLiteral() != null) {
            IntLiteralContext l = ctx.intLiteral();
            return new IntLiteralNode(l.INT_LITERAL().getText(), span(l.getStart(), l.getStop()));
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
        if (ctx.charLiteral() != null) {
            CharLiteralContext l = ctx.charLiteral();
            return new CharLiteralNode(l.CHAR_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        if (ctx.stringLiteral() != null) {
            StringLiteralContext l = ctx.stringLiteral();
            return new StringLiteralNode(l.STRING_LITERAL().getText(), span(l.getStart(), l.getStop()));
        }
        RawStringLiteralContext l = ctx.rawStringLiteral();
        return new RawStringLiteralNode(l.RAW_STRING_LITERAL().getText(), span(l.getStart(), l.getStop()));
    }

    /** End offset of a compilation-unit context excluding the virtual EOF token position. */
    private static Token lastMeaningfulStop(CompilationUnitContext ctx) {
        Token stop = ctx.getStop();
        if (stop == null || stop.getType() == Token.EOF) {
            Token last = null;
            last = laterOf(last, lastOf(ctx.functionDecl()));
            last = laterOf(last, lastOf(ctx.classDecl()));
            last = laterOf(last, lastOf(ctx.interfaceDecl()));
            return last == null ? ctx.getStart() : last;
        }
        return stop;
    }

    private static <T extends ParserRuleContext> T lastOf(List<T> contexts) {
        return contexts.isEmpty() ? null : contexts.get(contexts.size() - 1);
    }

    private static Token laterOf(Token current, ParserRuleContext candidate) {
        if (candidate == null) {
            return current;
        }
        Token stop = candidate.getStop();
        return current == null || stop.getStopIndex() > current.getStopIndex() ? stop : current;
    }

    static SourceSpan span(Token start, Token stop) {
        int first = Math.max(start.getStartIndex(), 0);
        int end = stop == null ? first : stop.getStopIndex() + 1;
        if (end < first) {
            end = first;
        }
        return SourceSpan.of(first, end);
    }
}
