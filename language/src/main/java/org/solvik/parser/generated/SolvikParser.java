/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
// Checkstyle: stop
//@formatter:off
package org.solvik.parser.generated;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "this-escape"})
public class SolvikParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		FUN=1, CLASS=2, INTERFACE=3, IMPLEMENTS=4, OPEN=5, EXTENDS=6, OVERRIDE=7, 
		INIT=8, THIS=9, SUPER=10, VAL=11, VAR=12, IF=13, ELSE=14, WHILE=15, FOR=16, 
		BREAK=17, CONTINUE=18, RETURN=19, BOOL_LITERAL=20, Identifier=21, INT_LITERAL=22, 
		LONG_LITERAL=23, FLOATING_LITERAL=24, CHAR_LITERAL=25, STRING_LITERAL=26, 
		LPAREN=27, RPAREN=28, LBRACE=29, RBRACE=30, SEMI=31, ASSIGN=32, COLON=33, 
		COMMA=34, DOT=35, NULLABLE_DOT=36, LBRACKET=37, RBRACKET=38, ADD=39, SUB=40, 
		MUL=41, DIV=42, BANG=43, EQ=44, NEQ=45, LT=46, LE=47, GT=48, GE=49, AND=50, 
		OR=51, WS=52, NEWLINE=53, LINE_COMMENT=54, BLOCK_COMMENT=55, RAW_STRING_LITERAL=56;
	public static final int
		RULE_compilationUnit = 0, RULE_functionDecl = 1, RULE_classDecl = 2, RULE_interfaceDecl = 3, 
		RULE_interfaceMember = 4, RULE_signatureDecl = 5, RULE_defaultMethodDecl = 6, 
		RULE_typeRefList = 7, RULE_classMember = 8, RULE_methodDecl = 9, RULE_methodModifier = 10, 
		RULE_propertyDecl = 11, RULE_initDecl = 12, RULE_parameterList = 13, RULE_parameter = 14, 
		RULE_typeRef = 15, RULE_block = 16, RULE_statement = 17, RULE_localDecl = 18, 
		RULE_bindingKind = 19, RULE_ifStmt = 20, RULE_elseBranch = 21, RULE_whileStmt = 22, 
		RULE_forStmt = 23, RULE_forInit = 24, RULE_forCondition = 25, RULE_forUpdate = 26, 
		RULE_localDeclNoSemi = 27, RULE_assignable = 28, RULE_breakStmt = 29, 
		RULE_continueStmt = 30, RULE_returnStmt = 31, RULE_exprStmt = 32, RULE_expression = 33, 
		RULE_logicalOr = 34, RULE_logicalAnd = 35, RULE_equality = 36, RULE_relational = 37, 
		RULE_additive = 38, RULE_multiplicative = 39, RULE_unary = 40, RULE_postfix = 41, 
		RULE_primary = 42, RULE_paren = 43, RULE_thisExpr = 44, RULE_superExpr = 45, 
		RULE_name = 46, RULE_suffix = 47, RULE_memberSuffix = 48, RULE_callSuffix = 49, 
		RULE_argumentList = 50, RULE_literal = 51, RULE_intLiteral = 52, RULE_longLiteral = 53, 
		RULE_floatingLiteral = 54, RULE_boolLiteral = 55, RULE_charLiteral = 56, 
		RULE_stringLiteral = 57, RULE_rawStringLiteral = 58;
	private static String[] makeRuleNames() {
		return new String[] {
			"compilationUnit", "functionDecl", "classDecl", "interfaceDecl", "interfaceMember", 
			"signatureDecl", "defaultMethodDecl", "typeRefList", "classMember", "methodDecl", 
			"methodModifier", "propertyDecl", "initDecl", "parameterList", "parameter", 
			"typeRef", "block", "statement", "localDecl", "bindingKind", "ifStmt", 
			"elseBranch", "whileStmt", "forStmt", "forInit", "forCondition", "forUpdate", 
			"localDeclNoSemi", "assignable", "breakStmt", "continueStmt", "returnStmt", 
			"exprStmt", "expression", "logicalOr", "logicalAnd", "equality", "relational", 
			"additive", "multiplicative", "unary", "postfix", "primary", "paren", 
			"thisExpr", "superExpr", "name", "suffix", "memberSuffix", "callSuffix", 
			"argumentList", "literal", "intLiteral", "longLiteral", "floatingLiteral", 
			"boolLiteral", "charLiteral", "stringLiteral", "rawStringLiteral"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'fun'", "'class'", "'interface'", "'implements'", "'open'", "'extends'", 
			"'override'", "'init'", "'this'", "'super'", "'val'", "'var'", "'if'", 
			"'else'", "'while'", "'for'", "'break'", "'continue'", "'return'", null, 
			null, null, null, null, null, null, "'('", "')'", "'{'", "'}'", "';'", 
			"'='", "':'", "','", "'.'", "'?.'", "'['", "']'", "'+'", "'-'", "'*'", 
			"'/'", "'!'", "'=='", "'!='", "'<'", "'<='", "'>'", "'>='", "'&&'", "'||'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "FUN", "CLASS", "INTERFACE", "IMPLEMENTS", "OPEN", "EXTENDS", "OVERRIDE", 
			"INIT", "THIS", "SUPER", "VAL", "VAR", "IF", "ELSE", "WHILE", "FOR", 
			"BREAK", "CONTINUE", "RETURN", "BOOL_LITERAL", "Identifier", "INT_LITERAL", 
			"LONG_LITERAL", "FLOATING_LITERAL", "CHAR_LITERAL", "STRING_LITERAL", 
			"LPAREN", "RPAREN", "LBRACE", "RBRACE", "SEMI", "ASSIGN", "COLON", "COMMA", 
			"DOT", "NULLABLE_DOT", "LBRACKET", "RBRACKET", "ADD", "SUB", "MUL", "DIV", 
			"BANG", "EQ", "NEQ", "LT", "LE", "GT", "GE", "AND", "OR", "WS", "NEWLINE", 
			"LINE_COMMENT", "BLOCK_COMMENT", "RAW_STRING_LITERAL"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Solvik.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public SolvikParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompilationUnitContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SolvikParser.EOF, 0); }
		public List<FunctionDeclContext> functionDecl() {
			return getRuleContexts(FunctionDeclContext.class);
		}
		public FunctionDeclContext functionDecl(int i) {
			return getRuleContext(FunctionDeclContext.class,i);
		}
		public List<ClassDeclContext> classDecl() {
			return getRuleContexts(ClassDeclContext.class);
		}
		public ClassDeclContext classDecl(int i) {
			return getRuleContext(ClassDeclContext.class,i);
		}
		public List<InterfaceDeclContext> interfaceDecl() {
			return getRuleContexts(InterfaceDeclContext.class);
		}
		public InterfaceDeclContext interfaceDecl(int i) {
			return getRuleContext(InterfaceDeclContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public CompilationUnitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_compilationUnit; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitCompilationUnit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CompilationUnitContext compilationUnit() throws RecognitionException {
		CompilationUnitContext _localctx = new CompilationUnitContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_compilationUnit);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(124);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2147483694L) != 0)) {
				{
				setState(122);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
					{
					setState(118);
					functionDecl();
					}
					break;
				case CLASS:
				case OPEN:
					{
					setState(119);
					classDecl();
					}
					break;
				case INTERFACE:
					{
					setState(120);
					interfaceDecl();
					}
					break;
				case SEMI:
					{
					setState(121);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(126);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(127);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionDeclContext extends ParserRuleContext {
		public TerminalNode FUN() { return getToken(SolvikParser.FUN, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public FunctionDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitFunctionDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionDeclContext functionDecl() throws RecognitionException {
		FunctionDeclContext _localctx = new FunctionDeclContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_functionDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(129);
			match(FUN);
			setState(130);
			match(Identifier);
			setState(131);
			match(LPAREN);
			setState(133);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(132);
				parameterList();
				}
			}

			setState(135);
			match(RPAREN);
			setState(136);
			match(COLON);
			setState(137);
			typeRef();
			setState(138);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ClassDeclContext extends ParserRuleContext {
		public TerminalNode CLASS() { return getToken(SolvikParser.CLASS, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LBRACE() { return getToken(SolvikParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(SolvikParser.RBRACE, 0); }
		public TerminalNode OPEN() { return getToken(SolvikParser.OPEN, 0); }
		public TerminalNode EXTENDS() { return getToken(SolvikParser.EXTENDS, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public TerminalNode IMPLEMENTS() { return getToken(SolvikParser.IMPLEMENTS, 0); }
		public TypeRefListContext typeRefList() {
			return getRuleContext(TypeRefListContext.class,0);
		}
		public List<ClassMemberContext> classMember() {
			return getRuleContexts(ClassMemberContext.class);
		}
		public ClassMemberContext classMember(int i) {
			return getRuleContext(ClassMemberContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public ClassDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitClassDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ClassDeclContext classDecl() throws RecognitionException {
		ClassDeclContext _localctx = new ClassDeclContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_classDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(141);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN) {
				{
				setState(140);
				match(OPEN);
				}
			}

			setState(143);
			match(CLASS);
			setState(144);
			match(Identifier);
			setState(147);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(145);
				match(EXTENDS);
				setState(146);
				typeRef();
				}
			}

			setState(151);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IMPLEMENTS) {
				{
				setState(149);
				match(IMPLEMENTS);
				setState(150);
				typeRefList();
				}
			}

			setState(153);
			match(LBRACE);
			setState(158);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2147490210L) != 0)) {
				{
				setState(156);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
				case OPEN:
				case OVERRIDE:
				case INIT:
				case VAL:
				case VAR:
					{
					setState(154);
					classMember();
					}
					break;
				case SEMI:
					{
					setState(155);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(160);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(161);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InterfaceDeclContext extends ParserRuleContext {
		public TerminalNode INTERFACE() { return getToken(SolvikParser.INTERFACE, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LBRACE() { return getToken(SolvikParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(SolvikParser.RBRACE, 0); }
		public TerminalNode EXTENDS() { return getToken(SolvikParser.EXTENDS, 0); }
		public TypeRefListContext typeRefList() {
			return getRuleContext(TypeRefListContext.class,0);
		}
		public List<InterfaceMemberContext> interfaceMember() {
			return getRuleContexts(InterfaceMemberContext.class);
		}
		public InterfaceMemberContext interfaceMember(int i) {
			return getRuleContext(InterfaceMemberContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public InterfaceDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_interfaceDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitInterfaceDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InterfaceDeclContext interfaceDecl() throws RecognitionException {
		InterfaceDeclContext _localctx = new InterfaceDeclContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_interfaceDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(163);
			match(INTERFACE);
			setState(164);
			match(Identifier);
			setState(167);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(165);
				match(EXTENDS);
				setState(166);
				typeRefList();
				}
			}

			setState(169);
			match(LBRACE);
			setState(174);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==FUN || _la==SEMI) {
				{
				setState(172);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
					{
					setState(170);
					interfaceMember();
					}
					break;
				case SEMI:
					{
					setState(171);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(176);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(177);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InterfaceMemberContext extends ParserRuleContext {
		public SignatureDeclContext signatureDecl() {
			return getRuleContext(SignatureDeclContext.class,0);
		}
		public DefaultMethodDeclContext defaultMethodDecl() {
			return getRuleContext(DefaultMethodDeclContext.class,0);
		}
		public InterfaceMemberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_interfaceMember; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitInterfaceMember(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InterfaceMemberContext interfaceMember() throws RecognitionException {
		InterfaceMemberContext _localctx = new InterfaceMemberContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_interfaceMember);
		try {
			setState(181);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(179);
				signatureDecl();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(180);
				defaultMethodDecl();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SignatureDeclContext extends ParserRuleContext {
		public TerminalNode FUN() { return getToken(SolvikParser.FUN, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public SignatureDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_signatureDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitSignatureDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SignatureDeclContext signatureDecl() throws RecognitionException {
		SignatureDeclContext _localctx = new SignatureDeclContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_signatureDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(183);
			match(FUN);
			setState(184);
			match(Identifier);
			setState(185);
			match(LPAREN);
			setState(187);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(186);
				parameterList();
				}
			}

			setState(189);
			match(RPAREN);
			setState(190);
			match(COLON);
			setState(191);
			typeRef();
			setState(192);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DefaultMethodDeclContext extends ParserRuleContext {
		public TerminalNode FUN() { return getToken(SolvikParser.FUN, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public DefaultMethodDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_defaultMethodDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitDefaultMethodDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DefaultMethodDeclContext defaultMethodDecl() throws RecognitionException {
		DefaultMethodDeclContext _localctx = new DefaultMethodDeclContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_defaultMethodDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(194);
			match(FUN);
			setState(195);
			match(Identifier);
			setState(196);
			match(LPAREN);
			setState(198);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(197);
				parameterList();
				}
			}

			setState(200);
			match(RPAREN);
			setState(201);
			match(COLON);
			setState(202);
			typeRef();
			setState(203);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeRefListContext extends ParserRuleContext {
		public List<TypeRefContext> typeRef() {
			return getRuleContexts(TypeRefContext.class);
		}
		public TypeRefContext typeRef(int i) {
			return getRuleContext(TypeRefContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
		public TypeRefListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeRefList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitTypeRefList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeRefListContext typeRefList() throws RecognitionException {
		TypeRefListContext _localctx = new TypeRefListContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_typeRefList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(205);
			typeRef();
			setState(210);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(206);
				match(COMMA);
				setState(207);
				typeRef();
				}
				}
				setState(212);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ClassMemberContext extends ParserRuleContext {
		public PropertyDeclContext propertyDecl() {
			return getRuleContext(PropertyDeclContext.class,0);
		}
		public InitDeclContext initDecl() {
			return getRuleContext(InitDeclContext.class,0);
		}
		public MethodDeclContext methodDecl() {
			return getRuleContext(MethodDeclContext.class,0);
		}
		public ClassMemberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classMember; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitClassMember(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ClassMemberContext classMember() throws RecognitionException {
		ClassMemberContext _localctx = new ClassMemberContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_classMember);
		try {
			setState(216);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(213);
				propertyDecl();
				}
				break;
			case INIT:
				enterOuterAlt(_localctx, 2);
				{
				setState(214);
				initDecl();
				}
				break;
			case FUN:
			case OPEN:
			case OVERRIDE:
				enterOuterAlt(_localctx, 3);
				{
				setState(215);
				methodDecl();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MethodDeclContext extends ParserRuleContext {
		public TerminalNode FUN() { return getToken(SolvikParser.FUN, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public List<MethodModifierContext> methodModifier() {
			return getRuleContexts(MethodModifierContext.class);
		}
		public MethodModifierContext methodModifier(int i) {
			return getRuleContext(MethodModifierContext.class,i);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public MethodDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_methodDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitMethodDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MethodDeclContext methodDecl() throws RecognitionException {
		MethodDeclContext _localctx = new MethodDeclContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_methodDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(221);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OPEN || _la==OVERRIDE) {
				{
				{
				setState(218);
				methodModifier();
				}
				}
				setState(223);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(224);
			match(FUN);
			setState(225);
			match(Identifier);
			setState(226);
			match(LPAREN);
			setState(228);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(227);
				parameterList();
				}
			}

			setState(230);
			match(RPAREN);
			setState(231);
			match(COLON);
			setState(232);
			typeRef();
			setState(233);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MethodModifierContext extends ParserRuleContext {
		public TerminalNode OPEN() { return getToken(SolvikParser.OPEN, 0); }
		public TerminalNode OVERRIDE() { return getToken(SolvikParser.OVERRIDE, 0); }
		public MethodModifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_methodModifier; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitMethodModifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MethodModifierContext methodModifier() throws RecognitionException {
		MethodModifierContext _localctx = new MethodModifierContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_methodModifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(235);
			_la = _input.LA(1);
			if ( !(_la==OPEN || _la==OVERRIDE) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PropertyDeclContext extends ParserRuleContext {
		public BindingKindContext bindingKind() {
			return getRuleContext(BindingKindContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public TerminalNode ASSIGN() { return getToken(SolvikParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public PropertyDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitPropertyDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyDeclContext propertyDecl() throws RecognitionException {
		PropertyDeclContext _localctx = new PropertyDeclContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_propertyDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(237);
			bindingKind();
			setState(238);
			match(Identifier);
			setState(239);
			match(COLON);
			setState(240);
			typeRef();
			setState(243);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(241);
				match(ASSIGN);
				setState(242);
				expression();
				}
			}

			setState(245);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InitDeclContext extends ParserRuleContext {
		public TerminalNode INIT() { return getToken(SolvikParser.INIT, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public InitDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_initDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitInitDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InitDeclContext initDecl() throws RecognitionException {
		InitDeclContext _localctx = new InitDeclContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_initDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(247);
			match(INIT);
			setState(248);
			match(LPAREN);
			setState(250);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(249);
				parameterList();
				}
			}

			setState(252);
			match(RPAREN);
			setState(253);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterListContext extends ParserRuleContext {
		public List<ParameterContext> parameter() {
			return getRuleContexts(ParameterContext.class);
		}
		public ParameterContext parameter(int i) {
			return getRuleContext(ParameterContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
		public ParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterListContext parameterList() throws RecognitionException {
		ParameterListContext _localctx = new ParameterListContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_parameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(255);
			parameter();
			setState(260);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(256);
				match(COMMA);
				setState(257);
				parameter();
				}
				}
				setState(262);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public ParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameter; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitParameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterContext parameter() throws RecognitionException {
		ParameterContext _localctx = new ParameterContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_parameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(263);
			match(Identifier);
			setState(264);
			match(COLON);
			setState(265);
			typeRef();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeRefContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TypeRefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeRef; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitTypeRef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeRefContext typeRef() throws RecognitionException {
		TypeRefContext _localctx = new TypeRefContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_typeRef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(267);
			match(Identifier);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(SolvikParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(SolvikParser.RBRACE, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			match(LBRACE);
			setState(274);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 72067492058480128L) != 0)) {
				{
				setState(272);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case THIS:
				case SUPER:
				case VAL:
				case VAR:
				case IF:
				case WHILE:
				case FOR:
				case BREAK:
				case CONTINUE:
				case RETURN:
				case BOOL_LITERAL:
				case Identifier:
				case INT_LITERAL:
				case LONG_LITERAL:
				case FLOATING_LITERAL:
				case CHAR_LITERAL:
				case STRING_LITERAL:
				case LPAREN:
				case SUB:
				case BANG:
				case RAW_STRING_LITERAL:
					{
					setState(270);
					statement();
					}
					break;
				case SEMI:
					{
					setState(271);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(276);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(277);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public LocalDeclContext localDecl() {
			return getRuleContext(LocalDeclContext.class,0);
		}
		public IfStmtContext ifStmt() {
			return getRuleContext(IfStmtContext.class,0);
		}
		public WhileStmtContext whileStmt() {
			return getRuleContext(WhileStmtContext.class,0);
		}
		public ForStmtContext forStmt() {
			return getRuleContext(ForStmtContext.class,0);
		}
		public BreakStmtContext breakStmt() {
			return getRuleContext(BreakStmtContext.class,0);
		}
		public ContinueStmtContext continueStmt() {
			return getRuleContext(ContinueStmtContext.class,0);
		}
		public ReturnStmtContext returnStmt() {
			return getRuleContext(ReturnStmtContext.class,0);
		}
		public ExprStmtContext exprStmt() {
			return getRuleContext(ExprStmtContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_statement);
		try {
			setState(287);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(279);
				localDecl();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 2);
				{
				setState(280);
				ifStmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 3);
				{
				setState(281);
				whileStmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 4);
				{
				setState(282);
				forStmt();
				}
				break;
			case BREAK:
				enterOuterAlt(_localctx, 5);
				{
				setState(283);
				breakStmt();
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 6);
				{
				setState(284);
				continueStmt();
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 7);
				{
				setState(285);
				returnStmt();
				}
				break;
			case THIS:
			case SUPER:
			case BOOL_LITERAL:
			case Identifier:
			case INT_LITERAL:
			case LONG_LITERAL:
			case FLOATING_LITERAL:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case LPAREN:
			case SUB:
			case BANG:
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 8);
				{
				setState(286);
				exprStmt();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LocalDeclContext extends ParserRuleContext {
		public BindingKindContext bindingKind() {
			return getRuleContext(BindingKindContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode ASSIGN() { return getToken(SolvikParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public LocalDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_localDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitLocalDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LocalDeclContext localDecl() throws RecognitionException {
		LocalDeclContext _localctx = new LocalDeclContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_localDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(289);
			bindingKind();
			setState(290);
			match(Identifier);
			setState(293);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(291);
				match(COLON);
				setState(292);
				typeRef();
				}
			}

			setState(295);
			match(ASSIGN);
			setState(296);
			expression();
			setState(297);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BindingKindContext extends ParserRuleContext {
		public TerminalNode VAL() { return getToken(SolvikParser.VAL, 0); }
		public TerminalNode VAR() { return getToken(SolvikParser.VAR, 0); }
		public BindingKindContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_bindingKind; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitBindingKind(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BindingKindContext bindingKind() throws RecognitionException {
		BindingKindContext _localctx = new BindingKindContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_bindingKind);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(299);
			_la = _input.LA(1);
			if ( !(_la==VAL || _la==VAR) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IfStmtContext extends ParserRuleContext {
		public TerminalNode IF() { return getToken(SolvikParser.IF, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ElseBranchContext elseBranch() {
			return getRuleContext(ElseBranchContext.class,0);
		}
		public IfStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitIfStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IfStmtContext ifStmt() throws RecognitionException {
		IfStmtContext _localctx = new IfStmtContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_ifStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(301);
			match(IF);
			setState(302);
			match(LPAREN);
			setState(303);
			expression();
			setState(304);
			match(RPAREN);
			setState(305);
			block();
			setState(307);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(306);
				elseBranch();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ElseBranchContext extends ParserRuleContext {
		public TerminalNode ELSE() { return getToken(SolvikParser.ELSE, 0); }
		public IfStmtContext ifStmt() {
			return getRuleContext(IfStmtContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ElseBranchContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elseBranch; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitElseBranch(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ElseBranchContext elseBranch() throws RecognitionException {
		ElseBranchContext _localctx = new ElseBranchContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_elseBranch);
		try {
			setState(313);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,26,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(309);
				match(ELSE);
				setState(310);
				ifStmt();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(311);
				match(ELSE);
				setState(312);
				block();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WhileStmtContext extends ParserRuleContext {
		public TerminalNode WHILE() { return getToken(SolvikParser.WHILE, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public WhileStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whileStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitWhileStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WhileStmtContext whileStmt() throws RecognitionException {
		WhileStmtContext _localctx = new WhileStmtContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_whileStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(315);
			match(WHILE);
			setState(316);
			match(LPAREN);
			setState(317);
			expression();
			setState(318);
			match(RPAREN);
			setState(319);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForStmtContext extends ParserRuleContext {
		public TerminalNode FOR() { return getToken(SolvikParser.FOR, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ForInitContext forInit() {
			return getRuleContext(ForInitContext.class,0);
		}
		public ForConditionContext forCondition() {
			return getRuleContext(ForConditionContext.class,0);
		}
		public ForUpdateContext forUpdate() {
			return getRuleContext(ForUpdateContext.class,0);
		}
		public ForStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitForStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForStmtContext forStmt() throws RecognitionException {
		ForStmtContext _localctx = new ForStmtContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_forStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(321);
			match(FOR);
			setState(322);
			match(LPAREN);
			setState(324);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 72067489909972480L) != 0)) {
				{
				setState(323);
				forInit();
				}
			}

			setState(326);
			match(SEMI);
			setState(328);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 72067489909966336L) != 0)) {
				{
				setState(327);
				forCondition();
				}
			}

			setState(330);
			match(SEMI);
			setState(332);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 72067489909966336L) != 0)) {
				{
				setState(331);
				forUpdate();
				}
			}

			setState(334);
			match(RPAREN);
			setState(335);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForInitContext extends ParserRuleContext {
		public LocalDeclNoSemiContext localDeclNoSemi() {
			return getRuleContext(LocalDeclNoSemiContext.class,0);
		}
		public AssignableContext assignable() {
			return getRuleContext(AssignableContext.class,0);
		}
		public ForInitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forInit; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitForInit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForInitContext forInit() throws RecognitionException {
		ForInitContext _localctx = new ForInitContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_forInit);
		try {
			setState(339);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(337);
				localDeclNoSemi();
				}
				break;
			case THIS:
			case SUPER:
			case BOOL_LITERAL:
			case Identifier:
			case INT_LITERAL:
			case LONG_LITERAL:
			case FLOATING_LITERAL:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case LPAREN:
			case SUB:
			case BANG:
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(338);
				assignable();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForConditionContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ForConditionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forCondition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitForCondition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForConditionContext forCondition() throws RecognitionException {
		ForConditionContext _localctx = new ForConditionContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_forCondition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(341);
			expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForUpdateContext extends ParserRuleContext {
		public AssignableContext assignable() {
			return getRuleContext(AssignableContext.class,0);
		}
		public ForUpdateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forUpdate; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitForUpdate(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForUpdateContext forUpdate() throws RecognitionException {
		ForUpdateContext _localctx = new ForUpdateContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_forUpdate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(343);
			assignable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LocalDeclNoSemiContext extends ParserRuleContext {
		public BindingKindContext bindingKind() {
			return getRuleContext(BindingKindContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode ASSIGN() { return getToken(SolvikParser.ASSIGN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public LocalDeclNoSemiContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_localDeclNoSemi; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitLocalDeclNoSemi(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LocalDeclNoSemiContext localDeclNoSemi() throws RecognitionException {
		LocalDeclNoSemiContext _localctx = new LocalDeclNoSemiContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_localDeclNoSemi);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(345);
			bindingKind();
			setState(346);
			match(Identifier);
			setState(349);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(347);
				match(COLON);
				setState(348);
				typeRef();
				}
			}

			setState(351);
			match(ASSIGN);
			setState(352);
			expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AssignableContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode ASSIGN() { return getToken(SolvikParser.ASSIGN, 0); }
		public AssignableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignable; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitAssignable(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignableContext assignable() throws RecognitionException {
		AssignableContext _localctx = new AssignableContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_assignable);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(354);
			expression();
			setState(357);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(355);
				match(ASSIGN);
				setState(356);
				expression();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BreakStmtContext extends ParserRuleContext {
		public TerminalNode BREAK() { return getToken(SolvikParser.BREAK, 0); }
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public BreakStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_breakStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitBreakStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BreakStmtContext breakStmt() throws RecognitionException {
		BreakStmtContext _localctx = new BreakStmtContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_breakStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(359);
			match(BREAK);
			setState(360);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ContinueStmtContext extends ParserRuleContext {
		public TerminalNode CONTINUE() { return getToken(SolvikParser.CONTINUE, 0); }
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public ContinueStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continueStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitContinueStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ContinueStmtContext continueStmt() throws RecognitionException {
		ContinueStmtContext _localctx = new ContinueStmtContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_continueStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(362);
			match(CONTINUE);
			setState(363);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReturnStmtContext extends ParserRuleContext {
		public TerminalNode RETURN() { return getToken(SolvikParser.RETURN, 0); }
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitReturnStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReturnStmtContext returnStmt() throws RecognitionException {
		ReturnStmtContext _localctx = new ReturnStmtContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_returnStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(365);
			match(RETURN);
			setState(367);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 72067489909966336L) != 0)) {
				{
				setState(366);
				expression();
				}
			}

			setState(369);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprStmtContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public TerminalNode ASSIGN() { return getToken(SolvikParser.ASSIGN, 0); }
		public ExprStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exprStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitExprStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprStmtContext exprStmt() throws RecognitionException {
		ExprStmtContext _localctx = new ExprStmtContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_exprStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(371);
			expression();
			setState(374);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(372);
				match(ASSIGN);
				setState(373);
				expression();
				}
			}

			setState(376);
			match(SEMI);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionContext extends ParserRuleContext {
		public LogicalOrContext logicalOr() {
			return getRuleContext(LogicalOrContext.class,0);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(378);
			logicalOr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LogicalOrContext extends ParserRuleContext {
		public List<LogicalAndContext> logicalAnd() {
			return getRuleContexts(LogicalAndContext.class);
		}
		public LogicalAndContext logicalAnd(int i) {
			return getRuleContext(LogicalAndContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(SolvikParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(SolvikParser.OR, i);
		}
		public LogicalOrContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logicalOr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitLogicalOr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LogicalOrContext logicalOr() throws RecognitionException {
		LogicalOrContext _localctx = new LogicalOrContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_logicalOr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(380);
			logicalAnd();
			setState(385);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(381);
				match(OR);
				setState(382);
				logicalAnd();
				}
				}
				setState(387);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LogicalAndContext extends ParserRuleContext {
		public List<EqualityContext> equality() {
			return getRuleContexts(EqualityContext.class);
		}
		public EqualityContext equality(int i) {
			return getRuleContext(EqualityContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(SolvikParser.AND); }
		public TerminalNode AND(int i) {
			return getToken(SolvikParser.AND, i);
		}
		public LogicalAndContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logicalAnd; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitLogicalAnd(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LogicalAndContext logicalAnd() throws RecognitionException {
		LogicalAndContext _localctx = new LogicalAndContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_logicalAnd);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(388);
			equality();
			setState(393);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(389);
				match(AND);
				setState(390);
				equality();
				}
				}
				setState(395);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EqualityContext extends ParserRuleContext {
		public List<RelationalContext> relational() {
			return getRuleContexts(RelationalContext.class);
		}
		public RelationalContext relational(int i) {
			return getRuleContext(RelationalContext.class,i);
		}
		public List<TerminalNode> EQ() { return getTokens(SolvikParser.EQ); }
		public TerminalNode EQ(int i) {
			return getToken(SolvikParser.EQ, i);
		}
		public List<TerminalNode> NEQ() { return getTokens(SolvikParser.NEQ); }
		public TerminalNode NEQ(int i) {
			return getToken(SolvikParser.NEQ, i);
		}
		public EqualityContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_equality; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitEquality(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EqualityContext equality() throws RecognitionException {
		EqualityContext _localctx = new EqualityContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_equality);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(396);
			relational();
			setState(401);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EQ || _la==NEQ) {
				{
				{
				setState(397);
				_la = _input.LA(1);
				if ( !(_la==EQ || _la==NEQ) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(398);
				relational();
				}
				}
				setState(403);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RelationalContext extends ParserRuleContext {
		public List<AdditiveContext> additive() {
			return getRuleContexts(AdditiveContext.class);
		}
		public AdditiveContext additive(int i) {
			return getRuleContext(AdditiveContext.class,i);
		}
		public List<TerminalNode> LT() { return getTokens(SolvikParser.LT); }
		public TerminalNode LT(int i) {
			return getToken(SolvikParser.LT, i);
		}
		public List<TerminalNode> LE() { return getTokens(SolvikParser.LE); }
		public TerminalNode LE(int i) {
			return getToken(SolvikParser.LE, i);
		}
		public List<TerminalNode> GT() { return getTokens(SolvikParser.GT); }
		public TerminalNode GT(int i) {
			return getToken(SolvikParser.GT, i);
		}
		public List<TerminalNode> GE() { return getTokens(SolvikParser.GE); }
		public TerminalNode GE(int i) {
			return getToken(SolvikParser.GE, i);
		}
		public RelationalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relational; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitRelational(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RelationalContext relational() throws RecognitionException {
		RelationalContext _localctx = new RelationalContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_relational);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(404);
			additive();
			setState(409);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1055531162664960L) != 0)) {
				{
				{
				setState(405);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1055531162664960L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(406);
				additive();
				}
				}
				setState(411);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AdditiveContext extends ParserRuleContext {
		public List<MultiplicativeContext> multiplicative() {
			return getRuleContexts(MultiplicativeContext.class);
		}
		public MultiplicativeContext multiplicative(int i) {
			return getRuleContext(MultiplicativeContext.class,i);
		}
		public List<TerminalNode> ADD() { return getTokens(SolvikParser.ADD); }
		public TerminalNode ADD(int i) {
			return getToken(SolvikParser.ADD, i);
		}
		public List<TerminalNode> SUB() { return getTokens(SolvikParser.SUB); }
		public TerminalNode SUB(int i) {
			return getToken(SolvikParser.SUB, i);
		}
		public AdditiveContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_additive; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitAdditive(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AdditiveContext additive() throws RecognitionException {
		AdditiveContext _localctx = new AdditiveContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_additive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(412);
			multiplicative();
			setState(417);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==SUB) {
				{
				{
				setState(413);
				_la = _input.LA(1);
				if ( !(_la==ADD || _la==SUB) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(414);
				multiplicative();
				}
				}
				setState(419);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MultiplicativeContext extends ParserRuleContext {
		public List<UnaryContext> unary() {
			return getRuleContexts(UnaryContext.class);
		}
		public UnaryContext unary(int i) {
			return getRuleContext(UnaryContext.class,i);
		}
		public List<TerminalNode> MUL() { return getTokens(SolvikParser.MUL); }
		public TerminalNode MUL(int i) {
			return getToken(SolvikParser.MUL, i);
		}
		public List<TerminalNode> DIV() { return getTokens(SolvikParser.DIV); }
		public TerminalNode DIV(int i) {
			return getToken(SolvikParser.DIV, i);
		}
		public MultiplicativeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_multiplicative; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitMultiplicative(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MultiplicativeContext multiplicative() throws RecognitionException {
		MultiplicativeContext _localctx = new MultiplicativeContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_multiplicative);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(420);
			unary();
			setState(425);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==MUL || _la==DIV) {
				{
				{
				setState(421);
				_la = _input.LA(1);
				if ( !(_la==MUL || _la==DIV) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(422);
				unary();
				}
				}
				setState(427);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnaryContext extends ParserRuleContext {
		public UnaryContext unary() {
			return getRuleContext(UnaryContext.class,0);
		}
		public TerminalNode BANG() { return getToken(SolvikParser.BANG, 0); }
		public TerminalNode SUB() { return getToken(SolvikParser.SUB, 0); }
		public PostfixContext postfix() {
			return getRuleContext(PostfixContext.class,0);
		}
		public UnaryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unary; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitUnary(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnaryContext unary() throws RecognitionException {
		UnaryContext _localctx = new UnaryContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_unary);
		int _la;
		try {
			setState(431);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SUB:
			case BANG:
				enterOuterAlt(_localctx, 1);
				{
				setState(428);
				_la = _input.LA(1);
				if ( !(_la==SUB || _la==BANG) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(429);
				unary();
				}
				break;
			case THIS:
			case SUPER:
			case BOOL_LITERAL:
			case Identifier:
			case INT_LITERAL:
			case LONG_LITERAL:
			case FLOATING_LITERAL:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case LPAREN:
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(430);
				postfix();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PostfixContext extends ParserRuleContext {
		public PrimaryContext primary() {
			return getRuleContext(PrimaryContext.class,0);
		}
		public List<SuffixContext> suffix() {
			return getRuleContexts(SuffixContext.class);
		}
		public SuffixContext suffix(int i) {
			return getRuleContext(SuffixContext.class,i);
		}
		public PostfixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_postfix; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitPostfix(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PostfixContext postfix() throws RecognitionException {
		PostfixContext _localctx = new PostfixContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_postfix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(433);
			primary();
			setState(437);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LPAREN || _la==DOT) {
				{
				{
				setState(434);
				suffix();
				}
				}
				setState(439);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PrimaryContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public ParenContext paren() {
			return getRuleContext(ParenContext.class,0);
		}
		public ThisExprContext thisExpr() {
			return getRuleContext(ThisExprContext.class,0);
		}
		public SuperExprContext superExpr() {
			return getRuleContext(SuperExprContext.class,0);
		}
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public PrimaryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primary; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitPrimary(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PrimaryContext primary() throws RecognitionException {
		PrimaryContext _localctx = new PrimaryContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_primary);
		try {
			setState(445);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BOOL_LITERAL:
			case INT_LITERAL:
			case LONG_LITERAL:
			case FLOATING_LITERAL:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(440);
				literal();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(441);
				paren();
				}
				break;
			case THIS:
				enterOuterAlt(_localctx, 3);
				{
				setState(442);
				thisExpr();
				}
				break;
			case SUPER:
				enterOuterAlt(_localctx, 4);
				{
				setState(443);
				superExpr();
				}
				break;
			case Identifier:
				enterOuterAlt(_localctx, 5);
				{
				setState(444);
				name();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParenContext extends ParserRuleContext {
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public ParenContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_paren; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitParen(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParenContext paren() throws RecognitionException {
		ParenContext _localctx = new ParenContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_paren);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(447);
			match(LPAREN);
			setState(448);
			expression();
			setState(449);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ThisExprContext extends ParserRuleContext {
		public TerminalNode THIS() { return getToken(SolvikParser.THIS, 0); }
		public ThisExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_thisExpr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitThisExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ThisExprContext thisExpr() throws RecognitionException {
		ThisExprContext _localctx = new ThisExprContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_thisExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(451);
			match(THIS);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SuperExprContext extends ParserRuleContext {
		public TerminalNode SUPER() { return getToken(SolvikParser.SUPER, 0); }
		public SuperExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_superExpr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitSuperExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SuperExprContext superExpr() throws RecognitionException {
		SuperExprContext _localctx = new SuperExprContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_superExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(453);
			match(SUPER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NameContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public NameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NameContext name() throws RecognitionException {
		NameContext _localctx = new NameContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(455);
			match(Identifier);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SuffixContext extends ParserRuleContext {
		public MemberSuffixContext memberSuffix() {
			return getRuleContext(MemberSuffixContext.class,0);
		}
		public CallSuffixContext callSuffix() {
			return getRuleContext(CallSuffixContext.class,0);
		}
		public SuffixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_suffix; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitSuffix(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SuffixContext suffix() throws RecognitionException {
		SuffixContext _localctx = new SuffixContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_suffix);
		try {
			setState(459);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(457);
				memberSuffix();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(458);
				callSuffix();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MemberSuffixContext extends ParserRuleContext {
		public TerminalNode DOT() { return getToken(SolvikParser.DOT, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public MemberSuffixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_memberSuffix; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitMemberSuffix(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MemberSuffixContext memberSuffix() throws RecognitionException {
		MemberSuffixContext _localctx = new MemberSuffixContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_memberSuffix);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(461);
			match(DOT);
			setState(462);
			match(Identifier);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CallSuffixContext extends ParserRuleContext {
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public CallSuffixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_callSuffix; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitCallSuffix(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CallSuffixContext callSuffix() throws RecognitionException {
		CallSuffixContext _localctx = new CallSuffixContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_callSuffix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(464);
			match(LPAREN);
			setState(466);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 72067489909966336L) != 0)) {
				{
				setState(465);
				argumentList();
				}
			}

			setState(468);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArgumentListContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
		public ArgumentListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitArgumentList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentListContext argumentList() throws RecognitionException {
		ArgumentListContext _localctx = new ArgumentListContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_argumentList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(470);
			expression();
			setState(475);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(471);
				match(COMMA);
				setState(472);
				expression();
				}
				}
				setState(477);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public IntLiteralContext intLiteral() {
			return getRuleContext(IntLiteralContext.class,0);
		}
		public LongLiteralContext longLiteral() {
			return getRuleContext(LongLiteralContext.class,0);
		}
		public FloatingLiteralContext floatingLiteral() {
			return getRuleContext(FloatingLiteralContext.class,0);
		}
		public BoolLiteralContext boolLiteral() {
			return getRuleContext(BoolLiteralContext.class,0);
		}
		public CharLiteralContext charLiteral() {
			return getRuleContext(CharLiteralContext.class,0);
		}
		public StringLiteralContext stringLiteral() {
			return getRuleContext(StringLiteralContext.class,0);
		}
		public RawStringLiteralContext rawStringLiteral() {
			return getRuleContext(RawStringLiteralContext.class,0);
		}
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_literal);
		try {
			setState(485);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(478);
				intLiteral();
				}
				break;
			case LONG_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(479);
				longLiteral();
				}
				break;
			case FLOATING_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(480);
				floatingLiteral();
				}
				break;
			case BOOL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(481);
				boolLiteral();
				}
				break;
			case CHAR_LITERAL:
				enterOuterAlt(_localctx, 5);
				{
				setState(482);
				charLiteral();
				}
				break;
			case STRING_LITERAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(483);
				stringLiteral();
				}
				break;
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(484);
				rawStringLiteral();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IntLiteralContext extends ParserRuleContext {
		public TerminalNode INT_LITERAL() { return getToken(SolvikParser.INT_LITERAL, 0); }
		public IntLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_intLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitIntLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IntLiteralContext intLiteral() throws RecognitionException {
		IntLiteralContext _localctx = new IntLiteralContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_intLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(487);
			match(INT_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LongLiteralContext extends ParserRuleContext {
		public TerminalNode LONG_LITERAL() { return getToken(SolvikParser.LONG_LITERAL, 0); }
		public LongLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_longLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitLongLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LongLiteralContext longLiteral() throws RecognitionException {
		LongLiteralContext _localctx = new LongLiteralContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_longLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(489);
			match(LONG_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FloatingLiteralContext extends ParserRuleContext {
		public TerminalNode FLOATING_LITERAL() { return getToken(SolvikParser.FLOATING_LITERAL, 0); }
		public FloatingLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_floatingLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitFloatingLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FloatingLiteralContext floatingLiteral() throws RecognitionException {
		FloatingLiteralContext _localctx = new FloatingLiteralContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_floatingLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(491);
			match(FLOATING_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BoolLiteralContext extends ParserRuleContext {
		public TerminalNode BOOL_LITERAL() { return getToken(SolvikParser.BOOL_LITERAL, 0); }
		public BoolLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_boolLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitBoolLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BoolLiteralContext boolLiteral() throws RecognitionException {
		BoolLiteralContext _localctx = new BoolLiteralContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_boolLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(493);
			match(BOOL_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CharLiteralContext extends ParserRuleContext {
		public TerminalNode CHAR_LITERAL() { return getToken(SolvikParser.CHAR_LITERAL, 0); }
		public CharLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_charLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitCharLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CharLiteralContext charLiteral() throws RecognitionException {
		CharLiteralContext _localctx = new CharLiteralContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_charLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(495);
			match(CHAR_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StringLiteralContext extends ParserRuleContext {
		public TerminalNode STRING_LITERAL() { return getToken(SolvikParser.STRING_LITERAL, 0); }
		public StringLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stringLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitStringLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StringLiteralContext stringLiteral() throws RecognitionException {
		StringLiteralContext _localctx = new StringLiteralContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_stringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(497);
			match(STRING_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RawStringLiteralContext extends ParserRuleContext {
		public TerminalNode RAW_STRING_LITERAL() { return getToken(SolvikParser.RAW_STRING_LITERAL, 0); }
		public RawStringLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_rawStringLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitRawStringLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RawStringLiteralContext rawStringLiteral() throws RecognitionException {
		RawStringLiteralContext _localctx = new RawStringLiteralContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_rawStringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(499);
			match(RAW_STRING_LITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u00018\u01f6\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
		"-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u00071\u0002"+
		"2\u00072\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u00076\u0002"+
		"7\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0005\u0000{\b\u0000\n\u0000\f\u0000~\t\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0003\u0001\u0086\b\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0002\u0003\u0002\u008e\b\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0003\u0002\u0094\b\u0002\u0001\u0002\u0001\u0002"+
		"\u0003\u0002\u0098\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002"+
		"\u009d\b\u0002\n\u0002\f\u0002\u00a0\t\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u00a8\b\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0005\u0003\u00ad\b\u0003\n\u0003\f\u0003"+
		"\u00b0\t\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0003\u0004"+
		"\u00b6\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005"+
		"\u00bc\b\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00c7\b\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0005\u0007\u00d1\b\u0007\n\u0007\f\u0007\u00d4"+
		"\t\u0007\u0001\b\u0001\b\u0001\b\u0003\b\u00d9\b\b\u0001\t\u0005\t\u00dc"+
		"\b\t\n\t\f\t\u00df\t\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\t\u00e5\b"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b"+
		"\u00f4\b\u000b\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001\f\u0003\f"+
		"\u00fb\b\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0005\r\u0103"+
		"\b\r\n\r\f\r\u0106\t\r\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0005\u0010"+
		"\u0111\b\u0010\n\u0010\f\u0010\u0114\t\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0003\u0011\u0120\b\u0011\u0001\u0012\u0001\u0012\u0001"+
		"\u0012\u0001\u0012\u0003\u0012\u0126\b\u0012\u0001\u0012\u0001\u0012\u0001"+
		"\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001"+
		"\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u0134\b\u0014\u0001"+
		"\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0003\u0015\u013a\b\u0015\u0001"+
		"\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u0145\b\u0017\u0001\u0017\u0001"+
		"\u0017\u0003\u0017\u0149\b\u0017\u0001\u0017\u0001\u0017\u0003\u0017\u014d"+
		"\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0018\u0001\u0018\u0003"+
		"\u0018\u0154\b\u0018\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0001"+
		"\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0003\u001b\u015e\b\u001b\u0001"+
		"\u001b\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0003"+
		"\u001c\u0166\b\u001c\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001e\u0001"+
		"\u001e\u0001\u001e\u0001\u001f\u0001\u001f\u0003\u001f\u0170\b\u001f\u0001"+
		"\u001f\u0001\u001f\u0001 \u0001 \u0001 \u0003 \u0177\b \u0001 \u0001 "+
		"\u0001!\u0001!\u0001\"\u0001\"\u0001\"\u0005\"\u0180\b\"\n\"\f\"\u0183"+
		"\t\"\u0001#\u0001#\u0001#\u0005#\u0188\b#\n#\f#\u018b\t#\u0001$\u0001"+
		"$\u0001$\u0005$\u0190\b$\n$\f$\u0193\t$\u0001%\u0001%\u0001%\u0005%\u0198"+
		"\b%\n%\f%\u019b\t%\u0001&\u0001&\u0001&\u0005&\u01a0\b&\n&\f&\u01a3\t"+
		"&\u0001\'\u0001\'\u0001\'\u0005\'\u01a8\b\'\n\'\f\'\u01ab\t\'\u0001(\u0001"+
		"(\u0001(\u0003(\u01b0\b(\u0001)\u0001)\u0005)\u01b4\b)\n)\f)\u01b7\t)"+
		"\u0001*\u0001*\u0001*\u0001*\u0001*\u0003*\u01be\b*\u0001+\u0001+\u0001"+
		"+\u0001+\u0001,\u0001,\u0001-\u0001-\u0001.\u0001.\u0001/\u0001/\u0003"+
		"/\u01cc\b/\u00010\u00010\u00010\u00011\u00011\u00031\u01d3\b1\u00011\u0001"+
		"1\u00012\u00012\u00012\u00052\u01da\b2\n2\f2\u01dd\t2\u00013\u00013\u0001"+
		"3\u00013\u00013\u00013\u00013\u00033\u01e6\b3\u00014\u00014\u00015\u0001"+
		"5\u00016\u00016\u00017\u00017\u00018\u00018\u00019\u00019\u0001:\u0001"+
		":\u0001:\u0000\u0000;\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012"+
		"\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\"+
		"^`bdfhjlnprt\u0000\u0007\u0002\u0000\u0005\u0005\u0007\u0007\u0001\u0000"+
		"\u000b\f\u0001\u0000,-\u0001\u0000.1\u0001\u0000\'(\u0001\u0000)*\u0002"+
		"\u0000((++\u01fb\u0000|\u0001\u0000\u0000\u0000\u0002\u0081\u0001\u0000"+
		"\u0000\u0000\u0004\u008d\u0001\u0000\u0000\u0000\u0006\u00a3\u0001\u0000"+
		"\u0000\u0000\b\u00b5\u0001\u0000\u0000\u0000\n\u00b7\u0001\u0000\u0000"+
		"\u0000\f\u00c2\u0001\u0000\u0000\u0000\u000e\u00cd\u0001\u0000\u0000\u0000"+
		"\u0010\u00d8\u0001\u0000\u0000\u0000\u0012\u00dd\u0001\u0000\u0000\u0000"+
		"\u0014\u00eb\u0001\u0000\u0000\u0000\u0016\u00ed\u0001\u0000\u0000\u0000"+
		"\u0018\u00f7\u0001\u0000\u0000\u0000\u001a\u00ff\u0001\u0000\u0000\u0000"+
		"\u001c\u0107\u0001\u0000\u0000\u0000\u001e\u010b\u0001\u0000\u0000\u0000"+
		" \u010d\u0001\u0000\u0000\u0000\"\u011f\u0001\u0000\u0000\u0000$\u0121"+
		"\u0001\u0000\u0000\u0000&\u012b\u0001\u0000\u0000\u0000(\u012d\u0001\u0000"+
		"\u0000\u0000*\u0139\u0001\u0000\u0000\u0000,\u013b\u0001\u0000\u0000\u0000"+
		".\u0141\u0001\u0000\u0000\u00000\u0153\u0001\u0000\u0000\u00002\u0155"+
		"\u0001\u0000\u0000\u00004\u0157\u0001\u0000\u0000\u00006\u0159\u0001\u0000"+
		"\u0000\u00008\u0162\u0001\u0000\u0000\u0000:\u0167\u0001\u0000\u0000\u0000"+
		"<\u016a\u0001\u0000\u0000\u0000>\u016d\u0001\u0000\u0000\u0000@\u0173"+
		"\u0001\u0000\u0000\u0000B\u017a\u0001\u0000\u0000\u0000D\u017c\u0001\u0000"+
		"\u0000\u0000F\u0184\u0001\u0000\u0000\u0000H\u018c\u0001\u0000\u0000\u0000"+
		"J\u0194\u0001\u0000\u0000\u0000L\u019c\u0001\u0000\u0000\u0000N\u01a4"+
		"\u0001\u0000\u0000\u0000P\u01af\u0001\u0000\u0000\u0000R\u01b1\u0001\u0000"+
		"\u0000\u0000T\u01bd\u0001\u0000\u0000\u0000V\u01bf\u0001\u0000\u0000\u0000"+
		"X\u01c3\u0001\u0000\u0000\u0000Z\u01c5\u0001\u0000\u0000\u0000\\\u01c7"+
		"\u0001\u0000\u0000\u0000^\u01cb\u0001\u0000\u0000\u0000`\u01cd\u0001\u0000"+
		"\u0000\u0000b\u01d0\u0001\u0000\u0000\u0000d\u01d6\u0001\u0000\u0000\u0000"+
		"f\u01e5\u0001\u0000\u0000\u0000h\u01e7\u0001\u0000\u0000\u0000j\u01e9"+
		"\u0001\u0000\u0000\u0000l\u01eb\u0001\u0000\u0000\u0000n\u01ed\u0001\u0000"+
		"\u0000\u0000p\u01ef\u0001\u0000\u0000\u0000r\u01f1\u0001\u0000\u0000\u0000"+
		"t\u01f3\u0001\u0000\u0000\u0000v{\u0003\u0002\u0001\u0000w{\u0003\u0004"+
		"\u0002\u0000x{\u0003\u0006\u0003\u0000y{\u0005\u001f\u0000\u0000zv\u0001"+
		"\u0000\u0000\u0000zw\u0001\u0000\u0000\u0000zx\u0001\u0000\u0000\u0000"+
		"zy\u0001\u0000\u0000\u0000{~\u0001\u0000\u0000\u0000|z\u0001\u0000\u0000"+
		"\u0000|}\u0001\u0000\u0000\u0000}\u007f\u0001\u0000\u0000\u0000~|\u0001"+
		"\u0000\u0000\u0000\u007f\u0080\u0005\u0000\u0000\u0001\u0080\u0001\u0001"+
		"\u0000\u0000\u0000\u0081\u0082\u0005\u0001\u0000\u0000\u0082\u0083\u0005"+
		"\u0015\u0000\u0000\u0083\u0085\u0005\u001b\u0000\u0000\u0084\u0086\u0003"+
		"\u001a\r\u0000\u0085\u0084\u0001\u0000\u0000\u0000\u0085\u0086\u0001\u0000"+
		"\u0000\u0000\u0086\u0087\u0001\u0000\u0000\u0000\u0087\u0088\u0005\u001c"+
		"\u0000\u0000\u0088\u0089\u0005!\u0000\u0000\u0089\u008a\u0003\u001e\u000f"+
		"\u0000\u008a\u008b\u0003 \u0010\u0000\u008b\u0003\u0001\u0000\u0000\u0000"+
		"\u008c\u008e\u0005\u0005\u0000\u0000\u008d\u008c\u0001\u0000\u0000\u0000"+
		"\u008d\u008e\u0001\u0000\u0000\u0000\u008e\u008f\u0001\u0000\u0000\u0000"+
		"\u008f\u0090\u0005\u0002\u0000\u0000\u0090\u0093\u0005\u0015\u0000\u0000"+
		"\u0091\u0092\u0005\u0006\u0000\u0000\u0092\u0094\u0003\u001e\u000f\u0000"+
		"\u0093\u0091\u0001\u0000\u0000\u0000\u0093\u0094\u0001\u0000\u0000\u0000"+
		"\u0094\u0097\u0001\u0000\u0000\u0000\u0095\u0096\u0005\u0004\u0000\u0000"+
		"\u0096\u0098\u0003\u000e\u0007\u0000\u0097\u0095\u0001\u0000\u0000\u0000"+
		"\u0097\u0098\u0001\u0000\u0000\u0000\u0098\u0099\u0001\u0000\u0000\u0000"+
		"\u0099\u009e\u0005\u001d\u0000\u0000\u009a\u009d\u0003\u0010\b\u0000\u009b"+
		"\u009d\u0005\u001f\u0000\u0000\u009c\u009a\u0001\u0000\u0000\u0000\u009c"+
		"\u009b\u0001\u0000\u0000\u0000\u009d\u00a0\u0001\u0000\u0000\u0000\u009e"+
		"\u009c\u0001\u0000\u0000\u0000\u009e\u009f\u0001\u0000\u0000\u0000\u009f"+
		"\u00a1\u0001\u0000\u0000\u0000\u00a0\u009e\u0001\u0000\u0000\u0000\u00a1"+
		"\u00a2\u0005\u001e\u0000\u0000\u00a2\u0005\u0001\u0000\u0000\u0000\u00a3"+
		"\u00a4\u0005\u0003\u0000\u0000\u00a4\u00a7\u0005\u0015\u0000\u0000\u00a5"+
		"\u00a6\u0005\u0006\u0000\u0000\u00a6\u00a8\u0003\u000e\u0007\u0000\u00a7"+
		"\u00a5\u0001\u0000\u0000\u0000\u00a7\u00a8\u0001\u0000\u0000\u0000\u00a8"+
		"\u00a9\u0001\u0000\u0000\u0000\u00a9\u00ae\u0005\u001d\u0000\u0000\u00aa"+
		"\u00ad\u0003\b\u0004\u0000\u00ab\u00ad\u0005\u001f\u0000\u0000\u00ac\u00aa"+
		"\u0001\u0000\u0000\u0000\u00ac\u00ab\u0001\u0000\u0000\u0000\u00ad\u00b0"+
		"\u0001\u0000\u0000\u0000\u00ae\u00ac\u0001\u0000\u0000\u0000\u00ae\u00af"+
		"\u0001\u0000\u0000\u0000\u00af\u00b1\u0001\u0000\u0000\u0000\u00b0\u00ae"+
		"\u0001\u0000\u0000\u0000\u00b1\u00b2\u0005\u001e\u0000\u0000\u00b2\u0007"+
		"\u0001\u0000\u0000\u0000\u00b3\u00b6\u0003\n\u0005\u0000\u00b4\u00b6\u0003"+
		"\f\u0006\u0000\u00b5\u00b3\u0001\u0000\u0000\u0000\u00b5\u00b4\u0001\u0000"+
		"\u0000\u0000\u00b6\t\u0001\u0000\u0000\u0000\u00b7\u00b8\u0005\u0001\u0000"+
		"\u0000\u00b8\u00b9\u0005\u0015\u0000\u0000\u00b9\u00bb\u0005\u001b\u0000"+
		"\u0000\u00ba\u00bc\u0003\u001a\r\u0000\u00bb\u00ba\u0001\u0000\u0000\u0000"+
		"\u00bb\u00bc\u0001\u0000\u0000\u0000\u00bc\u00bd\u0001\u0000\u0000\u0000"+
		"\u00bd\u00be\u0005\u001c\u0000\u0000\u00be\u00bf\u0005!\u0000\u0000\u00bf"+
		"\u00c0\u0003\u001e\u000f\u0000\u00c0\u00c1\u0005\u001f\u0000\u0000\u00c1"+
		"\u000b\u0001\u0000\u0000\u0000\u00c2\u00c3\u0005\u0001\u0000\u0000\u00c3"+
		"\u00c4\u0005\u0015\u0000\u0000\u00c4\u00c6\u0005\u001b\u0000\u0000\u00c5"+
		"\u00c7\u0003\u001a\r\u0000\u00c6\u00c5\u0001\u0000\u0000\u0000\u00c6\u00c7"+
		"\u0001\u0000\u0000\u0000\u00c7\u00c8\u0001\u0000\u0000\u0000\u00c8\u00c9"+
		"\u0005\u001c\u0000\u0000\u00c9\u00ca\u0005!\u0000\u0000\u00ca\u00cb\u0003"+
		"\u001e\u000f\u0000\u00cb\u00cc\u0003 \u0010\u0000\u00cc\r\u0001\u0000"+
		"\u0000\u0000\u00cd\u00d2\u0003\u001e\u000f\u0000\u00ce\u00cf\u0005\"\u0000"+
		"\u0000\u00cf\u00d1\u0003\u001e\u000f\u0000\u00d0\u00ce\u0001\u0000\u0000"+
		"\u0000\u00d1\u00d4\u0001\u0000\u0000\u0000\u00d2\u00d0\u0001\u0000\u0000"+
		"\u0000\u00d2\u00d3\u0001\u0000\u0000\u0000\u00d3\u000f\u0001\u0000\u0000"+
		"\u0000\u00d4\u00d2\u0001\u0000\u0000\u0000\u00d5\u00d9\u0003\u0016\u000b"+
		"\u0000\u00d6\u00d9\u0003\u0018\f\u0000\u00d7\u00d9\u0003\u0012\t\u0000"+
		"\u00d8\u00d5\u0001\u0000\u0000\u0000\u00d8\u00d6\u0001\u0000\u0000\u0000"+
		"\u00d8\u00d7\u0001\u0000\u0000\u0000\u00d9\u0011\u0001\u0000\u0000\u0000"+
		"\u00da\u00dc\u0003\u0014\n\u0000\u00db\u00da\u0001\u0000\u0000\u0000\u00dc"+
		"\u00df\u0001\u0000\u0000\u0000\u00dd\u00db\u0001\u0000\u0000\u0000\u00dd"+
		"\u00de\u0001\u0000\u0000\u0000\u00de\u00e0\u0001\u0000\u0000\u0000\u00df"+
		"\u00dd\u0001\u0000\u0000\u0000\u00e0\u00e1\u0005\u0001\u0000\u0000\u00e1"+
		"\u00e2\u0005\u0015\u0000\u0000\u00e2\u00e4\u0005\u001b\u0000\u0000\u00e3"+
		"\u00e5\u0003\u001a\r\u0000\u00e4\u00e3\u0001\u0000\u0000\u0000\u00e4\u00e5"+
		"\u0001\u0000\u0000\u0000\u00e5\u00e6\u0001\u0000\u0000\u0000\u00e6\u00e7"+
		"\u0005\u001c\u0000\u0000\u00e7\u00e8\u0005!\u0000\u0000\u00e8\u00e9\u0003"+
		"\u001e\u000f\u0000\u00e9\u00ea\u0003 \u0010\u0000\u00ea\u0013\u0001\u0000"+
		"\u0000\u0000\u00eb\u00ec\u0007\u0000\u0000\u0000\u00ec\u0015\u0001\u0000"+
		"\u0000\u0000\u00ed\u00ee\u0003&\u0013\u0000\u00ee\u00ef\u0005\u0015\u0000"+
		"\u0000\u00ef\u00f0\u0005!\u0000\u0000\u00f0\u00f3\u0003\u001e\u000f\u0000"+
		"\u00f1\u00f2\u0005 \u0000\u0000\u00f2\u00f4\u0003B!\u0000\u00f3\u00f1"+
		"\u0001\u0000\u0000\u0000\u00f3\u00f4\u0001\u0000\u0000\u0000\u00f4\u00f5"+
		"\u0001\u0000\u0000\u0000\u00f5\u00f6\u0005\u001f\u0000\u0000\u00f6\u0017"+
		"\u0001\u0000\u0000\u0000\u00f7\u00f8\u0005\b\u0000\u0000\u00f8\u00fa\u0005"+
		"\u001b\u0000\u0000\u00f9\u00fb\u0003\u001a\r\u0000\u00fa\u00f9\u0001\u0000"+
		"\u0000\u0000\u00fa\u00fb\u0001\u0000\u0000\u0000\u00fb\u00fc\u0001\u0000"+
		"\u0000\u0000\u00fc\u00fd\u0005\u001c\u0000\u0000\u00fd\u00fe\u0003 \u0010"+
		"\u0000\u00fe\u0019\u0001\u0000\u0000\u0000\u00ff\u0104\u0003\u001c\u000e"+
		"\u0000\u0100\u0101\u0005\"\u0000\u0000\u0101\u0103\u0003\u001c\u000e\u0000"+
		"\u0102\u0100\u0001\u0000\u0000\u0000\u0103\u0106\u0001\u0000\u0000\u0000"+
		"\u0104\u0102\u0001\u0000\u0000\u0000\u0104\u0105\u0001\u0000\u0000\u0000"+
		"\u0105\u001b\u0001\u0000\u0000\u0000\u0106\u0104\u0001\u0000\u0000\u0000"+
		"\u0107\u0108\u0005\u0015\u0000\u0000\u0108\u0109\u0005!\u0000\u0000\u0109"+
		"\u010a\u0003\u001e\u000f\u0000\u010a\u001d\u0001\u0000\u0000\u0000\u010b"+
		"\u010c\u0005\u0015\u0000\u0000\u010c\u001f\u0001\u0000\u0000\u0000\u010d"+
		"\u0112\u0005\u001d\u0000\u0000\u010e\u0111\u0003\"\u0011\u0000\u010f\u0111"+
		"\u0005\u001f\u0000\u0000\u0110\u010e\u0001\u0000\u0000\u0000\u0110\u010f"+
		"\u0001\u0000\u0000\u0000\u0111\u0114\u0001\u0000\u0000\u0000\u0112\u0110"+
		"\u0001\u0000\u0000\u0000\u0112\u0113\u0001\u0000\u0000\u0000\u0113\u0115"+
		"\u0001\u0000\u0000\u0000\u0114\u0112\u0001\u0000\u0000\u0000\u0115\u0116"+
		"\u0005\u001e\u0000\u0000\u0116!\u0001\u0000\u0000\u0000\u0117\u0120\u0003"+
		"$\u0012\u0000\u0118\u0120\u0003(\u0014\u0000\u0119\u0120\u0003,\u0016"+
		"\u0000\u011a\u0120\u0003.\u0017\u0000\u011b\u0120\u0003:\u001d\u0000\u011c"+
		"\u0120\u0003<\u001e\u0000\u011d\u0120\u0003>\u001f\u0000\u011e\u0120\u0003"+
		"@ \u0000\u011f\u0117\u0001\u0000\u0000\u0000\u011f\u0118\u0001\u0000\u0000"+
		"\u0000\u011f\u0119\u0001\u0000\u0000\u0000\u011f\u011a\u0001\u0000\u0000"+
		"\u0000\u011f\u011b\u0001\u0000\u0000\u0000\u011f\u011c\u0001\u0000\u0000"+
		"\u0000\u011f\u011d\u0001\u0000\u0000\u0000\u011f\u011e\u0001\u0000\u0000"+
		"\u0000\u0120#\u0001\u0000\u0000\u0000\u0121\u0122\u0003&\u0013\u0000\u0122"+
		"\u0125\u0005\u0015\u0000\u0000\u0123\u0124\u0005!\u0000\u0000\u0124\u0126"+
		"\u0003\u001e\u000f\u0000\u0125\u0123\u0001\u0000\u0000\u0000\u0125\u0126"+
		"\u0001\u0000\u0000\u0000\u0126\u0127\u0001\u0000\u0000\u0000\u0127\u0128"+
		"\u0005 \u0000\u0000\u0128\u0129\u0003B!\u0000\u0129\u012a\u0005\u001f"+
		"\u0000\u0000\u012a%\u0001\u0000\u0000\u0000\u012b\u012c\u0007\u0001\u0000"+
		"\u0000\u012c\'\u0001\u0000\u0000\u0000\u012d\u012e\u0005\r\u0000\u0000"+
		"\u012e\u012f\u0005\u001b\u0000\u0000\u012f\u0130\u0003B!\u0000\u0130\u0131"+
		"\u0005\u001c\u0000\u0000\u0131\u0133\u0003 \u0010\u0000\u0132\u0134\u0003"+
		"*\u0015\u0000\u0133\u0132\u0001\u0000\u0000\u0000\u0133\u0134\u0001\u0000"+
		"\u0000\u0000\u0134)\u0001\u0000\u0000\u0000\u0135\u0136\u0005\u000e\u0000"+
		"\u0000\u0136\u013a\u0003(\u0014\u0000\u0137\u0138\u0005\u000e\u0000\u0000"+
		"\u0138\u013a\u0003 \u0010\u0000\u0139\u0135\u0001\u0000\u0000\u0000\u0139"+
		"\u0137\u0001\u0000\u0000\u0000\u013a+\u0001\u0000\u0000\u0000\u013b\u013c"+
		"\u0005\u000f\u0000\u0000\u013c\u013d\u0005\u001b\u0000\u0000\u013d\u013e"+
		"\u0003B!\u0000\u013e\u013f\u0005\u001c\u0000\u0000\u013f\u0140\u0003 "+
		"\u0010\u0000\u0140-\u0001\u0000\u0000\u0000\u0141\u0142\u0005\u0010\u0000"+
		"\u0000\u0142\u0144\u0005\u001b\u0000\u0000\u0143\u0145\u00030\u0018\u0000"+
		"\u0144\u0143\u0001\u0000\u0000\u0000\u0144\u0145\u0001\u0000\u0000\u0000"+
		"\u0145\u0146\u0001\u0000\u0000\u0000\u0146\u0148\u0005\u001f\u0000\u0000"+
		"\u0147\u0149\u00032\u0019\u0000\u0148\u0147\u0001\u0000\u0000\u0000\u0148"+
		"\u0149\u0001\u0000\u0000\u0000\u0149\u014a\u0001\u0000\u0000\u0000\u014a"+
		"\u014c\u0005\u001f\u0000\u0000\u014b\u014d\u00034\u001a\u0000\u014c\u014b"+
		"\u0001\u0000\u0000\u0000\u014c\u014d\u0001\u0000\u0000\u0000\u014d\u014e"+
		"\u0001\u0000\u0000\u0000\u014e\u014f\u0005\u001c\u0000\u0000\u014f\u0150"+
		"\u0003 \u0010\u0000\u0150/\u0001\u0000\u0000\u0000\u0151\u0154\u00036"+
		"\u001b\u0000\u0152\u0154\u00038\u001c\u0000\u0153\u0151\u0001\u0000\u0000"+
		"\u0000\u0153\u0152\u0001\u0000\u0000\u0000\u01541\u0001\u0000\u0000\u0000"+
		"\u0155\u0156\u0003B!\u0000\u01563\u0001\u0000\u0000\u0000\u0157\u0158"+
		"\u00038\u001c\u0000\u01585\u0001\u0000\u0000\u0000\u0159\u015a\u0003&"+
		"\u0013\u0000\u015a\u015d\u0005\u0015\u0000\u0000\u015b\u015c\u0005!\u0000"+
		"\u0000\u015c\u015e\u0003\u001e\u000f\u0000\u015d\u015b\u0001\u0000\u0000"+
		"\u0000\u015d\u015e\u0001\u0000\u0000\u0000\u015e\u015f\u0001\u0000\u0000"+
		"\u0000\u015f\u0160\u0005 \u0000\u0000\u0160\u0161\u0003B!\u0000\u0161"+
		"7\u0001\u0000\u0000\u0000\u0162\u0165\u0003B!\u0000\u0163\u0164\u0005"+
		" \u0000\u0000\u0164\u0166\u0003B!\u0000\u0165\u0163\u0001\u0000\u0000"+
		"\u0000\u0165\u0166\u0001\u0000\u0000\u0000\u01669\u0001\u0000\u0000\u0000"+
		"\u0167\u0168\u0005\u0011\u0000\u0000\u0168\u0169\u0005\u001f\u0000\u0000"+
		"\u0169;\u0001\u0000\u0000\u0000\u016a\u016b\u0005\u0012\u0000\u0000\u016b"+
		"\u016c\u0005\u001f\u0000\u0000\u016c=\u0001\u0000\u0000\u0000\u016d\u016f"+
		"\u0005\u0013\u0000\u0000\u016e\u0170\u0003B!\u0000\u016f\u016e\u0001\u0000"+
		"\u0000\u0000\u016f\u0170\u0001\u0000\u0000\u0000\u0170\u0171\u0001\u0000"+
		"\u0000\u0000\u0171\u0172\u0005\u001f\u0000\u0000\u0172?\u0001\u0000\u0000"+
		"\u0000\u0173\u0176\u0003B!\u0000\u0174\u0175\u0005 \u0000\u0000\u0175"+
		"\u0177\u0003B!\u0000\u0176\u0174\u0001\u0000\u0000\u0000\u0176\u0177\u0001"+
		"\u0000\u0000\u0000\u0177\u0178\u0001\u0000\u0000\u0000\u0178\u0179\u0005"+
		"\u001f\u0000\u0000\u0179A\u0001\u0000\u0000\u0000\u017a\u017b\u0003D\""+
		"\u0000\u017bC\u0001\u0000\u0000\u0000\u017c\u0181\u0003F#\u0000\u017d"+
		"\u017e\u00053\u0000\u0000\u017e\u0180\u0003F#\u0000\u017f\u017d\u0001"+
		"\u0000\u0000\u0000\u0180\u0183\u0001\u0000\u0000\u0000\u0181\u017f\u0001"+
		"\u0000\u0000\u0000\u0181\u0182\u0001\u0000\u0000\u0000\u0182E\u0001\u0000"+
		"\u0000\u0000\u0183\u0181\u0001\u0000\u0000\u0000\u0184\u0189\u0003H$\u0000"+
		"\u0185\u0186\u00052\u0000\u0000\u0186\u0188\u0003H$\u0000\u0187\u0185"+
		"\u0001\u0000\u0000\u0000\u0188\u018b\u0001\u0000\u0000\u0000\u0189\u0187"+
		"\u0001\u0000\u0000\u0000\u0189\u018a\u0001\u0000\u0000\u0000\u018aG\u0001"+
		"\u0000\u0000\u0000\u018b\u0189\u0001\u0000\u0000\u0000\u018c\u0191\u0003"+
		"J%\u0000\u018d\u018e\u0007\u0002\u0000\u0000\u018e\u0190\u0003J%\u0000"+
		"\u018f\u018d\u0001\u0000\u0000\u0000\u0190\u0193\u0001\u0000\u0000\u0000"+
		"\u0191\u018f\u0001\u0000\u0000\u0000\u0191\u0192\u0001\u0000\u0000\u0000"+
		"\u0192I\u0001\u0000\u0000\u0000\u0193\u0191\u0001\u0000\u0000\u0000\u0194"+
		"\u0199\u0003L&\u0000\u0195\u0196\u0007\u0003\u0000\u0000\u0196\u0198\u0003"+
		"L&\u0000\u0197\u0195\u0001\u0000\u0000\u0000\u0198\u019b\u0001\u0000\u0000"+
		"\u0000\u0199\u0197\u0001\u0000\u0000\u0000\u0199\u019a\u0001\u0000\u0000"+
		"\u0000\u019aK\u0001\u0000\u0000\u0000\u019b\u0199\u0001\u0000\u0000\u0000"+
		"\u019c\u01a1\u0003N\'\u0000\u019d\u019e\u0007\u0004\u0000\u0000\u019e"+
		"\u01a0\u0003N\'\u0000\u019f\u019d\u0001\u0000\u0000\u0000\u01a0\u01a3"+
		"\u0001\u0000\u0000\u0000\u01a1\u019f\u0001\u0000\u0000\u0000\u01a1\u01a2"+
		"\u0001\u0000\u0000\u0000\u01a2M\u0001\u0000\u0000\u0000\u01a3\u01a1\u0001"+
		"\u0000\u0000\u0000\u01a4\u01a9\u0003P(\u0000\u01a5\u01a6\u0007\u0005\u0000"+
		"\u0000\u01a6\u01a8\u0003P(\u0000\u01a7\u01a5\u0001\u0000\u0000\u0000\u01a8"+
		"\u01ab\u0001\u0000\u0000\u0000\u01a9\u01a7\u0001\u0000\u0000\u0000\u01a9"+
		"\u01aa\u0001\u0000\u0000\u0000\u01aaO\u0001\u0000\u0000\u0000\u01ab\u01a9"+
		"\u0001\u0000\u0000\u0000\u01ac\u01ad\u0007\u0006\u0000\u0000\u01ad\u01b0"+
		"\u0003P(\u0000\u01ae\u01b0\u0003R)\u0000\u01af\u01ac\u0001\u0000\u0000"+
		"\u0000\u01af\u01ae\u0001\u0000\u0000\u0000\u01b0Q\u0001\u0000\u0000\u0000"+
		"\u01b1\u01b5\u0003T*\u0000\u01b2\u01b4\u0003^/\u0000\u01b3\u01b2\u0001"+
		"\u0000\u0000\u0000\u01b4\u01b7\u0001\u0000\u0000\u0000\u01b5\u01b3\u0001"+
		"\u0000\u0000\u0000\u01b5\u01b6\u0001\u0000\u0000\u0000\u01b6S\u0001\u0000"+
		"\u0000\u0000\u01b7\u01b5\u0001\u0000\u0000\u0000\u01b8\u01be\u0003f3\u0000"+
		"\u01b9\u01be\u0003V+\u0000\u01ba\u01be\u0003X,\u0000\u01bb\u01be\u0003"+
		"Z-\u0000\u01bc\u01be\u0003\\.\u0000\u01bd\u01b8\u0001\u0000\u0000\u0000"+
		"\u01bd\u01b9\u0001\u0000\u0000\u0000\u01bd\u01ba\u0001\u0000\u0000\u0000"+
		"\u01bd\u01bb\u0001\u0000\u0000\u0000\u01bd\u01bc\u0001\u0000\u0000\u0000"+
		"\u01beU\u0001\u0000\u0000\u0000\u01bf\u01c0\u0005\u001b\u0000\u0000\u01c0"+
		"\u01c1\u0003B!\u0000\u01c1\u01c2\u0005\u001c\u0000\u0000\u01c2W\u0001"+
		"\u0000\u0000\u0000\u01c3\u01c4\u0005\t\u0000\u0000\u01c4Y\u0001\u0000"+
		"\u0000\u0000\u01c5\u01c6\u0005\n\u0000\u0000\u01c6[\u0001\u0000\u0000"+
		"\u0000\u01c7\u01c8\u0005\u0015\u0000\u0000\u01c8]\u0001\u0000\u0000\u0000"+
		"\u01c9\u01cc\u0003`0\u0000\u01ca\u01cc\u0003b1\u0000\u01cb\u01c9\u0001"+
		"\u0000\u0000\u0000\u01cb\u01ca\u0001\u0000\u0000\u0000\u01cc_\u0001\u0000"+
		"\u0000\u0000\u01cd\u01ce\u0005#\u0000\u0000\u01ce\u01cf\u0005\u0015\u0000"+
		"\u0000\u01cfa\u0001\u0000\u0000\u0000\u01d0\u01d2\u0005\u001b\u0000\u0000"+
		"\u01d1\u01d3\u0003d2\u0000\u01d2\u01d1\u0001\u0000\u0000\u0000\u01d2\u01d3"+
		"\u0001\u0000\u0000\u0000\u01d3\u01d4\u0001\u0000\u0000\u0000\u01d4\u01d5"+
		"\u0005\u001c\u0000\u0000\u01d5c\u0001\u0000\u0000\u0000\u01d6\u01db\u0003"+
		"B!\u0000\u01d7\u01d8\u0005\"\u0000\u0000\u01d8\u01da\u0003B!\u0000\u01d9"+
		"\u01d7\u0001\u0000\u0000\u0000\u01da\u01dd\u0001\u0000\u0000\u0000\u01db"+
		"\u01d9\u0001\u0000\u0000\u0000\u01db\u01dc\u0001\u0000\u0000\u0000\u01dc"+
		"e\u0001\u0000\u0000\u0000\u01dd\u01db\u0001\u0000\u0000\u0000\u01de\u01e6"+
		"\u0003h4\u0000\u01df\u01e6\u0003j5\u0000\u01e0\u01e6\u0003l6\u0000\u01e1"+
		"\u01e6\u0003n7\u0000\u01e2\u01e6\u0003p8\u0000\u01e3\u01e6\u0003r9\u0000"+
		"\u01e4\u01e6\u0003t:\u0000\u01e5\u01de\u0001\u0000\u0000\u0000\u01e5\u01df"+
		"\u0001\u0000\u0000\u0000\u01e5\u01e0\u0001\u0000\u0000\u0000\u01e5\u01e1"+
		"\u0001\u0000\u0000\u0000\u01e5\u01e2\u0001\u0000\u0000\u0000\u01e5\u01e3"+
		"\u0001\u0000\u0000\u0000\u01e5\u01e4\u0001\u0000\u0000\u0000\u01e6g\u0001"+
		"\u0000\u0000\u0000\u01e7\u01e8\u0005\u0016\u0000\u0000\u01e8i\u0001\u0000"+
		"\u0000\u0000\u01e9\u01ea\u0005\u0017\u0000\u0000\u01eak\u0001\u0000\u0000"+
		"\u0000\u01eb\u01ec\u0005\u0018\u0000\u0000\u01ecm\u0001\u0000\u0000\u0000"+
		"\u01ed\u01ee\u0005\u0014\u0000\u0000\u01eeo\u0001\u0000\u0000\u0000\u01ef"+
		"\u01f0\u0005\u0019\u0000\u0000\u01f0q\u0001\u0000\u0000\u0000\u01f1\u01f2"+
		"\u0005\u001a\u0000\u0000\u01f2s\u0001\u0000\u0000\u0000\u01f3\u01f4\u0005"+
		"8\u0000\u0000\u01f4u\u0001\u0000\u0000\u00000z|\u0085\u008d\u0093\u0097"+
		"\u009c\u009e\u00a7\u00ac\u00ae\u00b5\u00bb\u00c6\u00d2\u00d8\u00dd\u00e4"+
		"\u00f3\u00fa\u0104\u0110\u0112\u011f\u0125\u0133\u0139\u0144\u0148\u014c"+
		"\u0153\u015d\u0165\u016f\u0176\u0181\u0189\u0191\u0199\u01a1\u01a9\u01af"+
		"\u01b5\u01bd\u01cb\u01d2\u01db\u01e5";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
