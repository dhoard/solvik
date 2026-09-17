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
// Checkstyle: stop
//@formatter:off
package org.solvik.parser.generated;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SolvikParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface SolvikVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link SolvikParser#compilationUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompilationUnit(SolvikParser.CompilationUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#functionDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDecl(SolvikParser.FunctionDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#classDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassDecl(SolvikParser.ClassDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#interfaceDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInterfaceDecl(SolvikParser.InterfaceDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#enumDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumDecl(SolvikParser.EnumDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#enumVariant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumVariant(SolvikParser.EnumVariantContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#interfaceMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInterfaceMember(SolvikParser.InterfaceMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#signatureDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSignatureDecl(SolvikParser.SignatureDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#defaultMethodDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefaultMethodDecl(SolvikParser.DefaultMethodDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#typeRefList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeRefList(SolvikParser.TypeRefListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#classMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassMember(SolvikParser.ClassMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#delegateDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelegateDecl(SolvikParser.DelegateDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#methodDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMethodDecl(SolvikParser.MethodDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#methodModifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMethodModifier(SolvikParser.MethodModifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#propertyDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyDecl(SolvikParser.PropertyDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#constructorDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstructorDecl(SolvikParser.ConstructorDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#parameterList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterList(SolvikParser.ParameterListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameter(SolvikParser.ParameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#typeParameterList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeParameterList(SolvikParser.TypeParameterListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#typeRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeRef(SolvikParser.TypeRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#typeArguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeArguments(SolvikParser.TypeArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(SolvikParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(SolvikParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#localDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLocalDecl(SolvikParser.LocalDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#bindingKind}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBindingKind(SolvikParser.BindingKindContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#ifStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStmt(SolvikParser.IfStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#elseBranch}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElseBranch(SolvikParser.ElseBranchContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#whileStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileStmt(SolvikParser.WhileStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#forStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForStmt(SolvikParser.ForStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#forInit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForInit(SolvikParser.ForInitContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#forCondition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForCondition(SolvikParser.ForConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#forUpdate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForUpdate(SolvikParser.ForUpdateContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#switchStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSwitchStmt(SolvikParser.SwitchStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#switchCase}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSwitchCase(SolvikParser.SwitchCaseContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#defaultCase}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefaultCase(SolvikParser.DefaultCaseContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#caseLabel}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCaseLabel(SolvikParser.CaseLabelContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#regexCaseLabel}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRegexCaseLabel(SolvikParser.RegexCaseLabelContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#localDeclNoSemi}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLocalDeclNoSemi(SolvikParser.LocalDeclNoSemiContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#assignable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignable(SolvikParser.AssignableContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#breakStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreakStmt(SolvikParser.BreakStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#continueStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinueStmt(SolvikParser.ContinueStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#returnStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturnStmt(SolvikParser.ReturnStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#exprStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprStmt(SolvikParser.ExprStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(SolvikParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#nullCoalescing}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullCoalescing(SolvikParser.NullCoalescingContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#logicalOr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalOr(SolvikParser.LogicalOrContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#logicalAnd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalAnd(SolvikParser.LogicalAndContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#equality}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEquality(SolvikParser.EqualityContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#relational}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelational(SolvikParser.RelationalContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#relation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelation(SolvikParser.RelationContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#additive}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditive(SolvikParser.AdditiveContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#multiplicative}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicative(SolvikParser.MultiplicativeContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#unary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnary(SolvikParser.UnaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#postfix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostfix(SolvikParser.PostfixContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimary(SolvikParser.PrimaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#matchExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMatchExpr(SolvikParser.MatchExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#matchBranch}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMatchBranch(SolvikParser.MatchBranchContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#pattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPattern(SolvikParser.PatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#patternList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPatternList(SolvikParser.PatternListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#paren}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParen(SolvikParser.ParenContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#thisExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitThisExpr(SolvikParser.ThisExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#superExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSuperExpr(SolvikParser.SuperExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitName(SolvikParser.NameContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#suffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSuffix(SolvikParser.SuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#memberSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberSuffix(SolvikParser.MemberSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#callSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCallSuffix(SolvikParser.CallSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#argumentList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgumentList(SolvikParser.ArgumentListContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(SolvikParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#intLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntLiteral(SolvikParser.IntLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#longLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLongLiteral(SolvikParser.LongLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#floatingLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFloatingLiteral(SolvikParser.FloatingLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#boolLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolLiteral(SolvikParser.BoolLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#charLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCharLiteral(SolvikParser.CharLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#stringLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringLiteral(SolvikParser.StringLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#rawStringLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRawStringLiteral(SolvikParser.RawStringLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link SolvikParser#nullLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullLiteral(SolvikParser.NullLiteralContext ctx);
}
