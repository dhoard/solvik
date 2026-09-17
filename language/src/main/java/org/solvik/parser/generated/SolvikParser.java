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
		FUN=1, CLASS=2, INTERFACE=3, DELEGATE=4, IMPLEMENTS=5, OPEN=6, EXTENDS=7, 
		OVERRIDE=8, INIT=9, THIS=10, SUPER=11, VAL=12, VAR=13, IF=14, ELSE=15, 
		WHILE=16, FOR=17, BREAK=18, CONTINUE=19, RETURN=20, NULL=21, IS=22, AS=23, 
		BOOL_LITERAL=24, Identifier=25, INT_LITERAL=26, LONG_LITERAL=27, FLOATING_LITERAL=28, 
		CHAR_LITERAL=29, STRING_LITERAL=30, LPAREN=31, RPAREN=32, LBRACE=33, RBRACE=34, 
		SEMI=35, ASSIGN=36, COLON=37, COMMA=38, DOT=39, NULLABLE_DOT=40, NULL_COALESCE=41, 
		QUESTION=42, LBRACKET=43, RBRACKET=44, ADD=45, SUB=46, MUL=47, DIV=48, 
		BANG=49, EQ=50, NEQ=51, LT=52, LE=53, GT=54, GE=55, AND=56, OR=57, WS=58, 
		NEWLINE=59, LINE_COMMENT=60, BLOCK_COMMENT=61, RAW_STRING_LITERAL=62;
	public static final int
		RULE_compilationUnit = 0, RULE_functionDecl = 1, RULE_classDecl = 2, RULE_interfaceDecl = 3, 
		RULE_interfaceMember = 4, RULE_signatureDecl = 5, RULE_defaultMethodDecl = 6, 
		RULE_typeRefList = 7, RULE_classMember = 8, RULE_delegateDecl = 9, RULE_methodDecl = 10, 
		RULE_methodModifier = 11, RULE_propertyDecl = 12, RULE_initDecl = 13, 
		RULE_parameterList = 14, RULE_parameter = 15, RULE_typeRef = 16, RULE_block = 17, 
		RULE_statement = 18, RULE_localDecl = 19, RULE_bindingKind = 20, RULE_ifStmt = 21, 
		RULE_elseBranch = 22, RULE_whileStmt = 23, RULE_forStmt = 24, RULE_forInit = 25, 
		RULE_forCondition = 26, RULE_forUpdate = 27, RULE_localDeclNoSemi = 28, 
		RULE_assignable = 29, RULE_breakStmt = 30, RULE_continueStmt = 31, RULE_returnStmt = 32, 
		RULE_exprStmt = 33, RULE_expression = 34, RULE_nullCoalescing = 35, RULE_logicalOr = 36, 
		RULE_logicalAnd = 37, RULE_equality = 38, RULE_relational = 39, RULE_relation = 40, 
		RULE_additive = 41, RULE_multiplicative = 42, RULE_unary = 43, RULE_postfix = 44, 
		RULE_primary = 45, RULE_paren = 46, RULE_thisExpr = 47, RULE_superExpr = 48, 
		RULE_name = 49, RULE_suffix = 50, RULE_memberSuffix = 51, RULE_callSuffix = 52, 
		RULE_argumentList = 53, RULE_literal = 54, RULE_intLiteral = 55, RULE_longLiteral = 56, 
		RULE_floatingLiteral = 57, RULE_boolLiteral = 58, RULE_charLiteral = 59, 
		RULE_stringLiteral = 60, RULE_rawStringLiteral = 61, RULE_nullLiteral = 62;
	private static String[] makeRuleNames() {
		return new String[] {
			"compilationUnit", "functionDecl", "classDecl", "interfaceDecl", "interfaceMember", 
			"signatureDecl", "defaultMethodDecl", "typeRefList", "classMember", "delegateDecl", 
			"methodDecl", "methodModifier", "propertyDecl", "initDecl", "parameterList", 
			"parameter", "typeRef", "block", "statement", "localDecl", "bindingKind", 
			"ifStmt", "elseBranch", "whileStmt", "forStmt", "forInit", "forCondition", 
			"forUpdate", "localDeclNoSemi", "assignable", "breakStmt", "continueStmt", 
			"returnStmt", "exprStmt", "expression", "nullCoalescing", "logicalOr", 
			"logicalAnd", "equality", "relational", "relation", "additive", "multiplicative", 
			"unary", "postfix", "primary", "paren", "thisExpr", "superExpr", "name", 
			"suffix", "memberSuffix", "callSuffix", "argumentList", "literal", "intLiteral", 
			"longLiteral", "floatingLiteral", "boolLiteral", "charLiteral", "stringLiteral", 
			"rawStringLiteral", "nullLiteral"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'fun'", "'class'", "'interface'", "'delegate'", "'implements'", 
			"'open'", "'extends'", "'override'", "'init'", "'this'", "'super'", "'val'", 
			"'var'", "'if'", "'else'", "'while'", "'for'", "'break'", "'continue'", 
			"'return'", "'null'", "'is'", "'as'", null, null, null, null, null, null, 
			null, "'('", "')'", "'{'", "'}'", "';'", "'='", "':'", "','", "'.'", 
			"'?.'", "'??'", "'?'", "'['", "']'", "'+'", "'-'", "'*'", "'/'", "'!'", 
			"'=='", "'!='", "'<'", "'<='", "'>'", "'>='", "'&&'", "'||'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "FUN", "CLASS", "INTERFACE", "DELEGATE", "IMPLEMENTS", "OPEN", 
			"EXTENDS", "OVERRIDE", "INIT", "THIS", "SUPER", "VAL", "VAR", "IF", "ELSE", 
			"WHILE", "FOR", "BREAK", "CONTINUE", "RETURN", "NULL", "IS", "AS", "BOOL_LITERAL", 
			"Identifier", "INT_LITERAL", "LONG_LITERAL", "FLOATING_LITERAL", "CHAR_LITERAL", 
			"STRING_LITERAL", "LPAREN", "RPAREN", "LBRACE", "RBRACE", "SEMI", "ASSIGN", 
			"COLON", "COMMA", "DOT", "NULLABLE_DOT", "NULL_COALESCE", "QUESTION", 
			"LBRACKET", "RBRACKET", "ADD", "SUB", "MUL", "DIV", "BANG", "EQ", "NEQ", 
			"LT", "LE", "GT", "GE", "AND", "OR", "WS", "NEWLINE", "LINE_COMMENT", 
			"BLOCK_COMMENT", "RAW_STRING_LITERAL"
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
			setState(132);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 34359738446L) != 0)) {
				{
				setState(130);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
					{
					setState(126);
					functionDecl();
					}
					break;
				case CLASS:
				case OPEN:
					{
					setState(127);
					classDecl();
					}
					break;
				case INTERFACE:
					{
					setState(128);
					interfaceDecl();
					}
					break;
				case SEMI:
					{
					setState(129);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(134);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(135);
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
			setState(137);
			match(FUN);
			setState(138);
			match(Identifier);
			setState(139);
			match(LPAREN);
			setState(141);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(140);
				parameterList();
				}
			}

			setState(143);
			match(RPAREN);
			setState(144);
			match(COLON);
			setState(145);
			typeRef();
			setState(146);
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
			setState(149);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN) {
				{
				setState(148);
				match(OPEN);
				}
			}

			setState(151);
			match(CLASS);
			setState(152);
			match(Identifier);
			setState(155);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(153);
				match(EXTENDS);
				setState(154);
				typeRef();
				}
			}

			setState(159);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IMPLEMENTS) {
				{
				setState(157);
				match(IMPLEMENTS);
				setState(158);
				typeRefList();
				}
			}

			setState(161);
			match(LBRACE);
			setState(166);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 34359751506L) != 0)) {
				{
				setState(164);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
				case DELEGATE:
				case OPEN:
				case OVERRIDE:
				case INIT:
				case VAL:
				case VAR:
					{
					setState(162);
					classMember();
					}
					break;
				case SEMI:
					{
					setState(163);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(168);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(169);
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
			setState(171);
			match(INTERFACE);
			setState(172);
			match(Identifier);
			setState(175);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(173);
				match(EXTENDS);
				setState(174);
				typeRefList();
				}
			}

			setState(177);
			match(LBRACE);
			setState(182);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==FUN || _la==SEMI) {
				{
				setState(180);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
					{
					setState(178);
					interfaceMember();
					}
					break;
				case SEMI:
					{
					setState(179);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(184);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(185);
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
			setState(189);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(187);
				signatureDecl();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(188);
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
			setState(191);
			match(FUN);
			setState(192);
			match(Identifier);
			setState(193);
			match(LPAREN);
			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(194);
				parameterList();
				}
			}

			setState(197);
			match(RPAREN);
			setState(198);
			match(COLON);
			setState(199);
			typeRef();
			setState(200);
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
			setState(202);
			match(FUN);
			setState(203);
			match(Identifier);
			setState(204);
			match(LPAREN);
			setState(206);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(205);
				parameterList();
				}
			}

			setState(208);
			match(RPAREN);
			setState(209);
			match(COLON);
			setState(210);
			typeRef();
			setState(211);
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
			setState(213);
			typeRef();
			setState(218);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(214);
				match(COMMA);
				setState(215);
				typeRef();
				}
				}
				setState(220);
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
		public DelegateDeclContext delegateDecl() {
			return getRuleContext(DelegateDeclContext.class,0);
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
			setState(225);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(221);
				propertyDecl();
				}
				break;
			case DELEGATE:
				enterOuterAlt(_localctx, 2);
				{
				setState(222);
				delegateDecl();
				}
				break;
			case INIT:
				enterOuterAlt(_localctx, 3);
				{
				setState(223);
				initDecl();
				}
				break;
			case FUN:
			case OPEN:
			case OVERRIDE:
				enterOuterAlt(_localctx, 4);
				{
				setState(224);
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
	public static class DelegateDeclContext extends ParserRuleContext {
		public TerminalNode DELEGATE() { return getToken(SolvikParser.DELEGATE, 0); }
		public TerminalNode VAL() { return getToken(SolvikParser.VAL, 0); }
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
		public DelegateDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_delegateDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitDelegateDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DelegateDeclContext delegateDecl() throws RecognitionException {
		DelegateDeclContext _localctx = new DelegateDeclContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_delegateDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(227);
			match(DELEGATE);
			setState(228);
			match(VAL);
			setState(229);
			match(Identifier);
			setState(230);
			match(COLON);
			setState(231);
			typeRef();
			setState(234);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(232);
				match(ASSIGN);
				setState(233);
				expression();
				}
			}

			setState(236);
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
		enterRule(_localctx, 20, RULE_methodDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(241);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OPEN || _la==OVERRIDE) {
				{
				{
				setState(238);
				methodModifier();
				}
				}
				setState(243);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(244);
			match(FUN);
			setState(245);
			match(Identifier);
			setState(246);
			match(LPAREN);
			setState(248);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(247);
				parameterList();
				}
			}

			setState(250);
			match(RPAREN);
			setState(251);
			match(COLON);
			setState(252);
			typeRef();
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
		enterRule(_localctx, 22, RULE_methodModifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(255);
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
		enterRule(_localctx, 24, RULE_propertyDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(257);
			bindingKind();
			setState(258);
			match(Identifier);
			setState(259);
			match(COLON);
			setState(260);
			typeRef();
			setState(263);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(261);
				match(ASSIGN);
				setState(262);
				expression();
				}
			}

			setState(265);
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
		enterRule(_localctx, 26, RULE_initDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(267);
			match(INIT);
			setState(268);
			match(LPAREN);
			setState(270);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(269);
				parameterList();
				}
			}

			setState(272);
			match(RPAREN);
			setState(273);
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
		enterRule(_localctx, 28, RULE_parameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(275);
			parameter();
			setState(280);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(276);
				match(COMMA);
				setState(277);
				parameter();
				}
				}
				setState(282);
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
		enterRule(_localctx, 30, RULE_parameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(283);
			match(Identifier);
			setState(284);
			match(COLON);
			setState(285);
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
		public TerminalNode QUESTION() { return getToken(SolvikParser.QUESTION, 0); }
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
		enterRule(_localctx, 32, RULE_typeRef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(287);
			match(Identifier);
			setState(289);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==QUESTION) {
				{
				setState(288);
				match(QUESTION);
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
		enterRule(_localctx, 34, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(291);
			match(LBRACE);
			setState(296);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4612319375767075840L) != 0)) {
				{
				setState(294);
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
				case NULL:
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
					setState(292);
					statement();
					}
					break;
				case SEMI:
					{
					setState(293);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(298);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(299);
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
		enterRule(_localctx, 36, RULE_statement);
		try {
			setState(309);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(301);
				localDecl();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 2);
				{
				setState(302);
				ifStmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 3);
				{
				setState(303);
				whileStmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 4);
				{
				setState(304);
				forStmt();
				}
				break;
			case BREAK:
				enterOuterAlt(_localctx, 5);
				{
				setState(305);
				breakStmt();
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 6);
				{
				setState(306);
				continueStmt();
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 7);
				{
				setState(307);
				returnStmt();
				}
				break;
			case THIS:
			case SUPER:
			case NULL:
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
				setState(308);
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
		enterRule(_localctx, 38, RULE_localDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(311);
			bindingKind();
			setState(312);
			match(Identifier);
			setState(315);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(313);
				match(COLON);
				setState(314);
				typeRef();
				}
			}

			setState(317);
			match(ASSIGN);
			setState(318);
			expression();
			setState(319);
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
		enterRule(_localctx, 40, RULE_bindingKind);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(321);
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
		enterRule(_localctx, 42, RULE_ifStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(323);
			match(IF);
			setState(324);
			match(LPAREN);
			setState(325);
			expression();
			setState(326);
			match(RPAREN);
			setState(327);
			block();
			setState(329);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(328);
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
		enterRule(_localctx, 44, RULE_elseBranch);
		try {
			setState(335);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,28,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(331);
				match(ELSE);
				setState(332);
				ifStmt();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(333);
				match(ELSE);
				setState(334);
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
		enterRule(_localctx, 46, RULE_whileStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(337);
			match(WHILE);
			setState(338);
			match(LPAREN);
			setState(339);
			expression();
			setState(340);
			match(RPAREN);
			setState(341);
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
		enterRule(_localctx, 48, RULE_forStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(343);
			match(FOR);
			setState(344);
			match(LPAREN);
			setState(346);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4612319341405289472L) != 0)) {
				{
				setState(345);
				forInit();
				}
			}

			setState(348);
			match(SEMI);
			setState(350);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4612319341405277184L) != 0)) {
				{
				setState(349);
				forCondition();
				}
			}

			setState(352);
			match(SEMI);
			setState(354);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4612319341405277184L) != 0)) {
				{
				setState(353);
				forUpdate();
				}
			}

			setState(356);
			match(RPAREN);
			setState(357);
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
		enterRule(_localctx, 50, RULE_forInit);
		try {
			setState(361);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(359);
				localDeclNoSemi();
				}
				break;
			case THIS:
			case SUPER:
			case NULL:
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
				setState(360);
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
		enterRule(_localctx, 52, RULE_forCondition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(363);
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
		enterRule(_localctx, 54, RULE_forUpdate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(365);
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
		enterRule(_localctx, 56, RULE_localDeclNoSemi);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(367);
			bindingKind();
			setState(368);
			match(Identifier);
			setState(371);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(369);
				match(COLON);
				setState(370);
				typeRef();
				}
			}

			setState(373);
			match(ASSIGN);
			setState(374);
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
		enterRule(_localctx, 58, RULE_assignable);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(376);
			expression();
			setState(379);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(377);
				match(ASSIGN);
				setState(378);
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
		enterRule(_localctx, 60, RULE_breakStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(381);
			match(BREAK);
			setState(382);
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
		enterRule(_localctx, 62, RULE_continueStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(384);
			match(CONTINUE);
			setState(385);
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
		enterRule(_localctx, 64, RULE_returnStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(387);
			match(RETURN);
			setState(389);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4612319341405277184L) != 0)) {
				{
				setState(388);
				expression();
				}
			}

			setState(391);
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
		enterRule(_localctx, 66, RULE_exprStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(393);
			expression();
			setState(396);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(394);
				match(ASSIGN);
				setState(395);
				expression();
				}
			}

			setState(398);
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
		public NullCoalescingContext nullCoalescing() {
			return getRuleContext(NullCoalescingContext.class,0);
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
		enterRule(_localctx, 68, RULE_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(400);
			nullCoalescing();
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
	public static class NullCoalescingContext extends ParserRuleContext {
		public List<LogicalOrContext> logicalOr() {
			return getRuleContexts(LogicalOrContext.class);
		}
		public LogicalOrContext logicalOr(int i) {
			return getRuleContext(LogicalOrContext.class,i);
		}
		public List<TerminalNode> NULL_COALESCE() { return getTokens(SolvikParser.NULL_COALESCE); }
		public TerminalNode NULL_COALESCE(int i) {
			return getToken(SolvikParser.NULL_COALESCE, i);
		}
		public NullCoalescingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nullCoalescing; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitNullCoalescing(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NullCoalescingContext nullCoalescing() throws RecognitionException {
		NullCoalescingContext _localctx = new NullCoalescingContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_nullCoalescing);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(402);
			logicalOr();
			setState(407);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NULL_COALESCE) {
				{
				{
				setState(403);
				match(NULL_COALESCE);
				setState(404);
				logicalOr();
				}
				}
				setState(409);
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
		enterRule(_localctx, 72, RULE_logicalOr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(410);
			logicalAnd();
			setState(415);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(411);
				match(OR);
				setState(412);
				logicalAnd();
				}
				}
				setState(417);
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
		enterRule(_localctx, 74, RULE_logicalAnd);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(418);
			equality();
			setState(423);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(419);
				match(AND);
				setState(420);
				equality();
				}
				}
				setState(425);
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
		enterRule(_localctx, 76, RULE_equality);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(426);
			relational();
			setState(431);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EQ || _la==NEQ) {
				{
				{
				setState(427);
				_la = _input.LA(1);
				if ( !(_la==EQ || _la==NEQ) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(428);
				relational();
				}
				}
				setState(433);
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
		public AdditiveContext additive() {
			return getRuleContext(AdditiveContext.class,0);
		}
		public List<RelationContext> relation() {
			return getRuleContexts(RelationContext.class);
		}
		public RelationContext relation(int i) {
			return getRuleContext(RelationContext.class,i);
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
		enterRule(_localctx, 78, RULE_relational);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(434);
			additive();
			setState(438);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 67553994423140352L) != 0)) {
				{
				{
				setState(435);
				relation();
				}
				}
				setState(440);
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
	public static class RelationContext extends ParserRuleContext {
		public AdditiveContext additive() {
			return getRuleContext(AdditiveContext.class,0);
		}
		public TerminalNode LT() { return getToken(SolvikParser.LT, 0); }
		public TerminalNode LE() { return getToken(SolvikParser.LE, 0); }
		public TerminalNode GT() { return getToken(SolvikParser.GT, 0); }
		public TerminalNode GE() { return getToken(SolvikParser.GE, 0); }
		public TerminalNode IS() { return getToken(SolvikParser.IS, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public TerminalNode AS() { return getToken(SolvikParser.AS, 0); }
		public RelationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relation; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitRelation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RelationContext relation() throws RecognitionException {
		RelationContext _localctx = new RelationContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_relation);
		int _la;
		try {
			setState(447);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LT:
			case LE:
			case GT:
			case GE:
				enterOuterAlt(_localctx, 1);
				{
				setState(441);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 67553994410557440L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(442);
				additive();
				}
				break;
			case IS:
				enterOuterAlt(_localctx, 2);
				{
				setState(443);
				match(IS);
				setState(444);
				typeRef();
				}
				break;
			case AS:
				enterOuterAlt(_localctx, 3);
				{
				setState(445);
				match(AS);
				setState(446);
				typeRef();
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
		enterRule(_localctx, 82, RULE_additive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(449);
			multiplicative();
			setState(454);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==SUB) {
				{
				{
				setState(450);
				_la = _input.LA(1);
				if ( !(_la==ADD || _la==SUB) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(451);
				multiplicative();
				}
				}
				setState(456);
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
		enterRule(_localctx, 84, RULE_multiplicative);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(457);
			unary();
			setState(462);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==MUL || _la==DIV) {
				{
				{
				setState(458);
				_la = _input.LA(1);
				if ( !(_la==MUL || _la==DIV) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(459);
				unary();
				}
				}
				setState(464);
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
		enterRule(_localctx, 86, RULE_unary);
		int _la;
		try {
			setState(468);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SUB:
			case BANG:
				enterOuterAlt(_localctx, 1);
				{
				setState(465);
				_la = _input.LA(1);
				if ( !(_la==SUB || _la==BANG) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(466);
				unary();
				}
				break;
			case THIS:
			case SUPER:
			case NULL:
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
				setState(467);
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
		enterRule(_localctx, 88, RULE_postfix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(470);
			primary();
			setState(474);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1651414925312L) != 0)) {
				{
				{
				setState(471);
				suffix();
				}
				}
				setState(476);
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
		enterRule(_localctx, 90, RULE_primary);
		try {
			setState(482);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NULL:
			case BOOL_LITERAL:
			case INT_LITERAL:
			case LONG_LITERAL:
			case FLOATING_LITERAL:
			case CHAR_LITERAL:
			case STRING_LITERAL:
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(477);
				literal();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(478);
				paren();
				}
				break;
			case THIS:
				enterOuterAlt(_localctx, 3);
				{
				setState(479);
				thisExpr();
				}
				break;
			case SUPER:
				enterOuterAlt(_localctx, 4);
				{
				setState(480);
				superExpr();
				}
				break;
			case Identifier:
				enterOuterAlt(_localctx, 5);
				{
				setState(481);
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
		enterRule(_localctx, 92, RULE_paren);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(484);
			match(LPAREN);
			setState(485);
			expression();
			setState(486);
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
		enterRule(_localctx, 94, RULE_thisExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(488);
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
		enterRule(_localctx, 96, RULE_superExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(490);
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
		enterRule(_localctx, 98, RULE_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(492);
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
		enterRule(_localctx, 100, RULE_suffix);
		try {
			setState(496);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DOT:
			case NULLABLE_DOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(494);
				memberSuffix();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(495);
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
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode DOT() { return getToken(SolvikParser.DOT, 0); }
		public TerminalNode NULLABLE_DOT() { return getToken(SolvikParser.NULLABLE_DOT, 0); }
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
		enterRule(_localctx, 102, RULE_memberSuffix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(498);
			_la = _input.LA(1);
			if ( !(_la==DOT || _la==NULLABLE_DOT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(499);
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
		enterRule(_localctx, 104, RULE_callSuffix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(501);
			match(LPAREN);
			setState(503);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4612319341405277184L) != 0)) {
				{
				setState(502);
				argumentList();
				}
			}

			setState(505);
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
		enterRule(_localctx, 106, RULE_argumentList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(507);
			expression();
			setState(512);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(508);
				match(COMMA);
				setState(509);
				expression();
				}
				}
				setState(514);
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
		public NullLiteralContext nullLiteral() {
			return getRuleContext(NullLiteralContext.class,0);
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
		enterRule(_localctx, 108, RULE_literal);
		try {
			setState(523);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(515);
				intLiteral();
				}
				break;
			case LONG_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(516);
				longLiteral();
				}
				break;
			case FLOATING_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(517);
				floatingLiteral();
				}
				break;
			case BOOL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(518);
				boolLiteral();
				}
				break;
			case CHAR_LITERAL:
				enterOuterAlt(_localctx, 5);
				{
				setState(519);
				charLiteral();
				}
				break;
			case STRING_LITERAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(520);
				stringLiteral();
				}
				break;
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(521);
				rawStringLiteral();
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 8);
				{
				setState(522);
				nullLiteral();
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
		enterRule(_localctx, 110, RULE_intLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(525);
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
		enterRule(_localctx, 112, RULE_longLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(527);
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
		enterRule(_localctx, 114, RULE_floatingLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(529);
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
		enterRule(_localctx, 116, RULE_boolLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(531);
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
		enterRule(_localctx, 118, RULE_charLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(533);
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
		enterRule(_localctx, 120, RULE_stringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(535);
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
		enterRule(_localctx, 122, RULE_rawStringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(537);
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

	@SuppressWarnings("CheckReturnValue")
	public static class NullLiteralContext extends ParserRuleContext {
		public TerminalNode NULL() { return getToken(SolvikParser.NULL, 0); }
		public NullLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nullLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitNullLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NullLiteralContext nullLiteral() throws RecognitionException {
		NullLiteralContext _localctx = new NullLiteralContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_nullLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(539);
			match(NULL);
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
		"\u0004\u0001>\u021e\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
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
		"7\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007;\u0002"+
		"<\u0007<\u0002=\u0007=\u0002>\u0007>\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0005\u0000\u0083\b\u0000\n\u0000\f\u0000\u0086\t\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003"+
		"\u0001\u008e\b\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0002\u0003\u0002\u0096\b\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0003\u0002\u009c\b\u0002\u0001\u0002\u0001\u0002\u0003"+
		"\u0002\u00a0\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002\u00a5"+
		"\b\u0002\n\u0002\f\u0002\u00a8\t\u0002\u0001\u0002\u0001\u0002\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u00b0\b\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0005\u0003\u00b5\b\u0003\n\u0003\f\u0003\u00b8"+
		"\t\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0003\u0004\u00be"+
		"\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005\u00c4"+
		"\b\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006\u00cf\b\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0005\u0007\u00d9\b\u0007\n\u0007\f\u0007\u00dc\t\u0007"+
		"\u0001\b\u0001\b\u0001\b\u0001\b\u0003\b\u00e2\b\b\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003\t\u00eb\b\t\u0001\t\u0001\t\u0001"+
		"\n\u0005\n\u00f0\b\n\n\n\f\n\u00f3\t\n\u0001\n\u0001\n\u0001\n\u0001\n"+
		"\u0003\n\u00f9\b\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b"+
		"\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0003\f\u0108"+
		"\b\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0003\r\u010f\b\r\u0001\r"+
		"\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u0117"+
		"\b\u000e\n\u000e\f\u000e\u011a\t\u000e\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u0010\u0001\u0010\u0003\u0010\u0122\b\u0010\u0001\u0011"+
		"\u0001\u0011\u0001\u0011\u0005\u0011\u0127\b\u0011\n\u0011\f\u0011\u012a"+
		"\t\u0011\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u0136"+
		"\b\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0003\u0013\u013c"+
		"\b\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014\u0001"+
		"\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001"+
		"\u0015\u0003\u0015\u014a\b\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001"+
		"\u0016\u0003\u0016\u0150\b\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0003"+
		"\u0018\u015b\b\u0018\u0001\u0018\u0001\u0018\u0003\u0018\u015f\b\u0018"+
		"\u0001\u0018\u0001\u0018\u0003\u0018\u0163\b\u0018\u0001\u0018\u0001\u0018"+
		"\u0001\u0018\u0001\u0019\u0001\u0019\u0003\u0019\u016a\b\u0019\u0001\u001a"+
		"\u0001\u001a\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c"+
		"\u0001\u001c\u0003\u001c\u0174\b\u001c\u0001\u001c\u0001\u001c\u0001\u001c"+
		"\u0001\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u017c\b\u001d\u0001\u001e"+
		"\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f\u0001\u001f\u0001 \u0001"+
		" \u0003 \u0186\b \u0001 \u0001 \u0001!\u0001!\u0001!\u0003!\u018d\b!\u0001"+
		"!\u0001!\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0005#\u0196\b#\n#\f#\u0199"+
		"\t#\u0001$\u0001$\u0001$\u0005$\u019e\b$\n$\f$\u01a1\t$\u0001%\u0001%"+
		"\u0001%\u0005%\u01a6\b%\n%\f%\u01a9\t%\u0001&\u0001&\u0001&\u0005&\u01ae"+
		"\b&\n&\f&\u01b1\t&\u0001\'\u0001\'\u0005\'\u01b5\b\'\n\'\f\'\u01b8\t\'"+
		"\u0001(\u0001(\u0001(\u0001(\u0001(\u0001(\u0003(\u01c0\b(\u0001)\u0001"+
		")\u0001)\u0005)\u01c5\b)\n)\f)\u01c8\t)\u0001*\u0001*\u0001*\u0005*\u01cd"+
		"\b*\n*\f*\u01d0\t*\u0001+\u0001+\u0001+\u0003+\u01d5\b+\u0001,\u0001,"+
		"\u0005,\u01d9\b,\n,\f,\u01dc\t,\u0001-\u0001-\u0001-\u0001-\u0001-\u0003"+
		"-\u01e3\b-\u0001.\u0001.\u0001.\u0001.\u0001/\u0001/\u00010\u00010\u0001"+
		"1\u00011\u00012\u00012\u00032\u01f1\b2\u00013\u00013\u00013\u00014\u0001"+
		"4\u00034\u01f8\b4\u00014\u00014\u00015\u00015\u00015\u00055\u01ff\b5\n"+
		"5\f5\u0202\t5\u00016\u00016\u00016\u00016\u00016\u00016\u00016\u00016"+
		"\u00036\u020c\b6\u00017\u00017\u00018\u00018\u00019\u00019\u0001:\u0001"+
		":\u0001;\u0001;\u0001<\u0001<\u0001=\u0001=\u0001>\u0001>\u0001>\u0000"+
		"\u0000?\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtvxz|\u0000"+
		"\b\u0002\u0000\u0006\u0006\b\b\u0001\u0000\f\r\u0001\u000023\u0001\u0000"+
		"47\u0001\u0000-.\u0001\u0000/0\u0002\u0000..11\u0001\u0000\'(\u0226\u0000"+
		"\u0084\u0001\u0000\u0000\u0000\u0002\u0089\u0001\u0000\u0000\u0000\u0004"+
		"\u0095\u0001\u0000\u0000\u0000\u0006\u00ab\u0001\u0000\u0000\u0000\b\u00bd"+
		"\u0001\u0000\u0000\u0000\n\u00bf\u0001\u0000\u0000\u0000\f\u00ca\u0001"+
		"\u0000\u0000\u0000\u000e\u00d5\u0001\u0000\u0000\u0000\u0010\u00e1\u0001"+
		"\u0000\u0000\u0000\u0012\u00e3\u0001\u0000\u0000\u0000\u0014\u00f1\u0001"+
		"\u0000\u0000\u0000\u0016\u00ff\u0001\u0000\u0000\u0000\u0018\u0101\u0001"+
		"\u0000\u0000\u0000\u001a\u010b\u0001\u0000\u0000\u0000\u001c\u0113\u0001"+
		"\u0000\u0000\u0000\u001e\u011b\u0001\u0000\u0000\u0000 \u011f\u0001\u0000"+
		"\u0000\u0000\"\u0123\u0001\u0000\u0000\u0000$\u0135\u0001\u0000\u0000"+
		"\u0000&\u0137\u0001\u0000\u0000\u0000(\u0141\u0001\u0000\u0000\u0000*"+
		"\u0143\u0001\u0000\u0000\u0000,\u014f\u0001\u0000\u0000\u0000.\u0151\u0001"+
		"\u0000\u0000\u00000\u0157\u0001\u0000\u0000\u00002\u0169\u0001\u0000\u0000"+
		"\u00004\u016b\u0001\u0000\u0000\u00006\u016d\u0001\u0000\u0000\u00008"+
		"\u016f\u0001\u0000\u0000\u0000:\u0178\u0001\u0000\u0000\u0000<\u017d\u0001"+
		"\u0000\u0000\u0000>\u0180\u0001\u0000\u0000\u0000@\u0183\u0001\u0000\u0000"+
		"\u0000B\u0189\u0001\u0000\u0000\u0000D\u0190\u0001\u0000\u0000\u0000F"+
		"\u0192\u0001\u0000\u0000\u0000H\u019a\u0001\u0000\u0000\u0000J\u01a2\u0001"+
		"\u0000\u0000\u0000L\u01aa\u0001\u0000\u0000\u0000N\u01b2\u0001\u0000\u0000"+
		"\u0000P\u01bf\u0001\u0000\u0000\u0000R\u01c1\u0001\u0000\u0000\u0000T"+
		"\u01c9\u0001\u0000\u0000\u0000V\u01d4\u0001\u0000\u0000\u0000X\u01d6\u0001"+
		"\u0000\u0000\u0000Z\u01e2\u0001\u0000\u0000\u0000\\\u01e4\u0001\u0000"+
		"\u0000\u0000^\u01e8\u0001\u0000\u0000\u0000`\u01ea\u0001\u0000\u0000\u0000"+
		"b\u01ec\u0001\u0000\u0000\u0000d\u01f0\u0001\u0000\u0000\u0000f\u01f2"+
		"\u0001\u0000\u0000\u0000h\u01f5\u0001\u0000\u0000\u0000j\u01fb\u0001\u0000"+
		"\u0000\u0000l\u020b\u0001\u0000\u0000\u0000n\u020d\u0001\u0000\u0000\u0000"+
		"p\u020f\u0001\u0000\u0000\u0000r\u0211\u0001\u0000\u0000\u0000t\u0213"+
		"\u0001\u0000\u0000\u0000v\u0215\u0001\u0000\u0000\u0000x\u0217\u0001\u0000"+
		"\u0000\u0000z\u0219\u0001\u0000\u0000\u0000|\u021b\u0001\u0000\u0000\u0000"+
		"~\u0083\u0003\u0002\u0001\u0000\u007f\u0083\u0003\u0004\u0002\u0000\u0080"+
		"\u0083\u0003\u0006\u0003\u0000\u0081\u0083\u0005#\u0000\u0000\u0082~\u0001"+
		"\u0000\u0000\u0000\u0082\u007f\u0001\u0000\u0000\u0000\u0082\u0080\u0001"+
		"\u0000\u0000\u0000\u0082\u0081\u0001\u0000\u0000\u0000\u0083\u0086\u0001"+
		"\u0000\u0000\u0000\u0084\u0082\u0001\u0000\u0000\u0000\u0084\u0085\u0001"+
		"\u0000\u0000\u0000\u0085\u0087\u0001\u0000\u0000\u0000\u0086\u0084\u0001"+
		"\u0000\u0000\u0000\u0087\u0088\u0005\u0000\u0000\u0001\u0088\u0001\u0001"+
		"\u0000\u0000\u0000\u0089\u008a\u0005\u0001\u0000\u0000\u008a\u008b\u0005"+
		"\u0019\u0000\u0000\u008b\u008d\u0005\u001f\u0000\u0000\u008c\u008e\u0003"+
		"\u001c\u000e\u0000\u008d\u008c\u0001\u0000\u0000\u0000\u008d\u008e\u0001"+
		"\u0000\u0000\u0000\u008e\u008f\u0001\u0000\u0000\u0000\u008f\u0090\u0005"+
		" \u0000\u0000\u0090\u0091\u0005%\u0000\u0000\u0091\u0092\u0003 \u0010"+
		"\u0000\u0092\u0093\u0003\"\u0011\u0000\u0093\u0003\u0001\u0000\u0000\u0000"+
		"\u0094\u0096\u0005\u0006\u0000\u0000\u0095\u0094\u0001\u0000\u0000\u0000"+
		"\u0095\u0096\u0001\u0000\u0000\u0000\u0096\u0097\u0001\u0000\u0000\u0000"+
		"\u0097\u0098\u0005\u0002\u0000\u0000\u0098\u009b\u0005\u0019\u0000\u0000"+
		"\u0099\u009a\u0005\u0007\u0000\u0000\u009a\u009c\u0003 \u0010\u0000\u009b"+
		"\u0099\u0001\u0000\u0000\u0000\u009b\u009c\u0001\u0000\u0000\u0000\u009c"+
		"\u009f\u0001\u0000\u0000\u0000\u009d\u009e\u0005\u0005\u0000\u0000\u009e"+
		"\u00a0\u0003\u000e\u0007\u0000\u009f\u009d\u0001\u0000\u0000\u0000\u009f"+
		"\u00a0\u0001\u0000\u0000\u0000\u00a0\u00a1\u0001\u0000\u0000\u0000\u00a1"+
		"\u00a6\u0005!\u0000\u0000\u00a2\u00a5\u0003\u0010\b\u0000\u00a3\u00a5"+
		"\u0005#\u0000\u0000\u00a4\u00a2\u0001\u0000\u0000\u0000\u00a4\u00a3\u0001"+
		"\u0000\u0000\u0000\u00a5\u00a8\u0001\u0000\u0000\u0000\u00a6\u00a4\u0001"+
		"\u0000\u0000\u0000\u00a6\u00a7\u0001\u0000\u0000\u0000\u00a7\u00a9\u0001"+
		"\u0000\u0000\u0000\u00a8\u00a6\u0001\u0000\u0000\u0000\u00a9\u00aa\u0005"+
		"\"\u0000\u0000\u00aa\u0005\u0001\u0000\u0000\u0000\u00ab\u00ac\u0005\u0003"+
		"\u0000\u0000\u00ac\u00af\u0005\u0019\u0000\u0000\u00ad\u00ae\u0005\u0007"+
		"\u0000\u0000\u00ae\u00b0\u0003\u000e\u0007\u0000\u00af\u00ad\u0001\u0000"+
		"\u0000\u0000\u00af\u00b0\u0001\u0000\u0000\u0000\u00b0\u00b1\u0001\u0000"+
		"\u0000\u0000\u00b1\u00b6\u0005!\u0000\u0000\u00b2\u00b5\u0003\b\u0004"+
		"\u0000\u00b3\u00b5\u0005#\u0000\u0000\u00b4\u00b2\u0001\u0000\u0000\u0000"+
		"\u00b4\u00b3\u0001\u0000\u0000\u0000\u00b5\u00b8\u0001\u0000\u0000\u0000"+
		"\u00b6\u00b4\u0001\u0000\u0000\u0000\u00b6\u00b7\u0001\u0000\u0000\u0000"+
		"\u00b7\u00b9\u0001\u0000\u0000\u0000\u00b8\u00b6\u0001\u0000\u0000\u0000"+
		"\u00b9\u00ba\u0005\"\u0000\u0000\u00ba\u0007\u0001\u0000\u0000\u0000\u00bb"+
		"\u00be\u0003\n\u0005\u0000\u00bc\u00be\u0003\f\u0006\u0000\u00bd\u00bb"+
		"\u0001\u0000\u0000\u0000\u00bd\u00bc\u0001\u0000\u0000\u0000\u00be\t\u0001"+
		"\u0000\u0000\u0000\u00bf\u00c0\u0005\u0001\u0000\u0000\u00c0\u00c1\u0005"+
		"\u0019\u0000\u0000\u00c1\u00c3\u0005\u001f\u0000\u0000\u00c2\u00c4\u0003"+
		"\u001c\u000e\u0000\u00c3\u00c2\u0001\u0000\u0000\u0000\u00c3\u00c4\u0001"+
		"\u0000\u0000\u0000\u00c4\u00c5\u0001\u0000\u0000\u0000\u00c5\u00c6\u0005"+
		" \u0000\u0000\u00c6\u00c7\u0005%\u0000\u0000\u00c7\u00c8\u0003 \u0010"+
		"\u0000\u00c8\u00c9\u0005#\u0000\u0000\u00c9\u000b\u0001\u0000\u0000\u0000"+
		"\u00ca\u00cb\u0005\u0001\u0000\u0000\u00cb\u00cc\u0005\u0019\u0000\u0000"+
		"\u00cc\u00ce\u0005\u001f\u0000\u0000\u00cd\u00cf\u0003\u001c\u000e\u0000"+
		"\u00ce\u00cd\u0001\u0000\u0000\u0000\u00ce\u00cf\u0001\u0000\u0000\u0000"+
		"\u00cf\u00d0\u0001\u0000\u0000\u0000\u00d0\u00d1\u0005 \u0000\u0000\u00d1"+
		"\u00d2\u0005%\u0000\u0000\u00d2\u00d3\u0003 \u0010\u0000\u00d3\u00d4\u0003"+
		"\"\u0011\u0000\u00d4\r\u0001\u0000\u0000\u0000\u00d5\u00da\u0003 \u0010"+
		"\u0000\u00d6\u00d7\u0005&\u0000\u0000\u00d7\u00d9\u0003 \u0010\u0000\u00d8"+
		"\u00d6\u0001\u0000\u0000\u0000\u00d9\u00dc\u0001\u0000\u0000\u0000\u00da"+
		"\u00d8\u0001\u0000\u0000\u0000\u00da\u00db\u0001\u0000\u0000\u0000\u00db"+
		"\u000f\u0001\u0000\u0000\u0000\u00dc\u00da\u0001\u0000\u0000\u0000\u00dd"+
		"\u00e2\u0003\u0018\f\u0000\u00de\u00e2\u0003\u0012\t\u0000\u00df\u00e2"+
		"\u0003\u001a\r\u0000\u00e0\u00e2\u0003\u0014\n\u0000\u00e1\u00dd\u0001"+
		"\u0000\u0000\u0000\u00e1\u00de\u0001\u0000\u0000\u0000\u00e1\u00df\u0001"+
		"\u0000\u0000\u0000\u00e1\u00e0\u0001\u0000\u0000\u0000\u00e2\u0011\u0001"+
		"\u0000\u0000\u0000\u00e3\u00e4\u0005\u0004\u0000\u0000\u00e4\u00e5\u0005"+
		"\f\u0000\u0000\u00e5\u00e6\u0005\u0019\u0000\u0000\u00e6\u00e7\u0005%"+
		"\u0000\u0000\u00e7\u00ea\u0003 \u0010\u0000\u00e8\u00e9\u0005$\u0000\u0000"+
		"\u00e9\u00eb\u0003D\"\u0000\u00ea\u00e8\u0001\u0000\u0000\u0000\u00ea"+
		"\u00eb\u0001\u0000\u0000\u0000\u00eb\u00ec\u0001\u0000\u0000\u0000\u00ec"+
		"\u00ed\u0005#\u0000\u0000\u00ed\u0013\u0001\u0000\u0000\u0000\u00ee\u00f0"+
		"\u0003\u0016\u000b\u0000\u00ef\u00ee\u0001\u0000\u0000\u0000\u00f0\u00f3"+
		"\u0001\u0000\u0000\u0000\u00f1\u00ef\u0001\u0000\u0000\u0000\u00f1\u00f2"+
		"\u0001\u0000\u0000\u0000\u00f2\u00f4\u0001\u0000\u0000\u0000\u00f3\u00f1"+
		"\u0001\u0000\u0000\u0000\u00f4\u00f5\u0005\u0001\u0000\u0000\u00f5\u00f6"+
		"\u0005\u0019\u0000\u0000\u00f6\u00f8\u0005\u001f\u0000\u0000\u00f7\u00f9"+
		"\u0003\u001c\u000e\u0000\u00f8\u00f7\u0001\u0000\u0000\u0000\u00f8\u00f9"+
		"\u0001\u0000\u0000\u0000\u00f9\u00fa\u0001\u0000\u0000\u0000\u00fa\u00fb"+
		"\u0005 \u0000\u0000\u00fb\u00fc\u0005%\u0000\u0000\u00fc\u00fd\u0003 "+
		"\u0010\u0000\u00fd\u00fe\u0003\"\u0011\u0000\u00fe\u0015\u0001\u0000\u0000"+
		"\u0000\u00ff\u0100\u0007\u0000\u0000\u0000\u0100\u0017\u0001\u0000\u0000"+
		"\u0000\u0101\u0102\u0003(\u0014\u0000\u0102\u0103\u0005\u0019\u0000\u0000"+
		"\u0103\u0104\u0005%\u0000\u0000\u0104\u0107\u0003 \u0010\u0000\u0105\u0106"+
		"\u0005$\u0000\u0000\u0106\u0108\u0003D\"\u0000\u0107\u0105\u0001\u0000"+
		"\u0000\u0000\u0107\u0108\u0001\u0000\u0000\u0000\u0108\u0109\u0001\u0000"+
		"\u0000\u0000\u0109\u010a\u0005#\u0000\u0000\u010a\u0019\u0001\u0000\u0000"+
		"\u0000\u010b\u010c\u0005\t\u0000\u0000\u010c\u010e\u0005\u001f\u0000\u0000"+
		"\u010d\u010f\u0003\u001c\u000e\u0000\u010e\u010d\u0001\u0000\u0000\u0000"+
		"\u010e\u010f\u0001\u0000\u0000\u0000\u010f\u0110\u0001\u0000\u0000\u0000"+
		"\u0110\u0111\u0005 \u0000\u0000\u0111\u0112\u0003\"\u0011\u0000\u0112"+
		"\u001b\u0001\u0000\u0000\u0000\u0113\u0118\u0003\u001e\u000f\u0000\u0114"+
		"\u0115\u0005&\u0000\u0000\u0115\u0117\u0003\u001e\u000f\u0000\u0116\u0114"+
		"\u0001\u0000\u0000\u0000\u0117\u011a\u0001\u0000\u0000\u0000\u0118\u0116"+
		"\u0001\u0000\u0000\u0000\u0118\u0119\u0001\u0000\u0000\u0000\u0119\u001d"+
		"\u0001\u0000\u0000\u0000\u011a\u0118\u0001\u0000\u0000\u0000\u011b\u011c"+
		"\u0005\u0019\u0000\u0000\u011c\u011d\u0005%\u0000\u0000\u011d\u011e\u0003"+
		" \u0010\u0000\u011e\u001f\u0001\u0000\u0000\u0000\u011f\u0121\u0005\u0019"+
		"\u0000\u0000\u0120\u0122\u0005*\u0000\u0000\u0121\u0120\u0001\u0000\u0000"+
		"\u0000\u0121\u0122\u0001\u0000\u0000\u0000\u0122!\u0001\u0000\u0000\u0000"+
		"\u0123\u0128\u0005!\u0000\u0000\u0124\u0127\u0003$\u0012\u0000\u0125\u0127"+
		"\u0005#\u0000\u0000\u0126\u0124\u0001\u0000\u0000\u0000\u0126\u0125\u0001"+
		"\u0000\u0000\u0000\u0127\u012a\u0001\u0000\u0000\u0000\u0128\u0126\u0001"+
		"\u0000\u0000\u0000\u0128\u0129\u0001\u0000\u0000\u0000\u0129\u012b\u0001"+
		"\u0000\u0000\u0000\u012a\u0128\u0001\u0000\u0000\u0000\u012b\u012c\u0005"+
		"\"\u0000\u0000\u012c#\u0001\u0000\u0000\u0000\u012d\u0136\u0003&\u0013"+
		"\u0000\u012e\u0136\u0003*\u0015\u0000\u012f\u0136\u0003.\u0017\u0000\u0130"+
		"\u0136\u00030\u0018\u0000\u0131\u0136\u0003<\u001e\u0000\u0132\u0136\u0003"+
		">\u001f\u0000\u0133\u0136\u0003@ \u0000\u0134\u0136\u0003B!\u0000\u0135"+
		"\u012d\u0001\u0000\u0000\u0000\u0135\u012e\u0001\u0000\u0000\u0000\u0135"+
		"\u012f\u0001\u0000\u0000\u0000\u0135\u0130\u0001\u0000\u0000\u0000\u0135"+
		"\u0131\u0001\u0000\u0000\u0000\u0135\u0132\u0001\u0000\u0000\u0000\u0135"+
		"\u0133\u0001\u0000\u0000\u0000\u0135\u0134\u0001\u0000\u0000\u0000\u0136"+
		"%\u0001\u0000\u0000\u0000\u0137\u0138\u0003(\u0014\u0000\u0138\u013b\u0005"+
		"\u0019\u0000\u0000\u0139\u013a\u0005%\u0000\u0000\u013a\u013c\u0003 \u0010"+
		"\u0000\u013b\u0139\u0001\u0000\u0000\u0000\u013b\u013c\u0001\u0000\u0000"+
		"\u0000\u013c\u013d\u0001\u0000\u0000\u0000\u013d\u013e\u0005$\u0000\u0000"+
		"\u013e\u013f\u0003D\"\u0000\u013f\u0140\u0005#\u0000\u0000\u0140\'\u0001"+
		"\u0000\u0000\u0000\u0141\u0142\u0007\u0001\u0000\u0000\u0142)\u0001\u0000"+
		"\u0000\u0000\u0143\u0144\u0005\u000e\u0000\u0000\u0144\u0145\u0005\u001f"+
		"\u0000\u0000\u0145\u0146\u0003D\"\u0000\u0146\u0147\u0005 \u0000\u0000"+
		"\u0147\u0149\u0003\"\u0011\u0000\u0148\u014a\u0003,\u0016\u0000\u0149"+
		"\u0148\u0001\u0000\u0000\u0000\u0149\u014a\u0001\u0000\u0000\u0000\u014a"+
		"+\u0001\u0000\u0000\u0000\u014b\u014c\u0005\u000f\u0000\u0000\u014c\u0150"+
		"\u0003*\u0015\u0000\u014d\u014e\u0005\u000f\u0000\u0000\u014e\u0150\u0003"+
		"\"\u0011\u0000\u014f\u014b\u0001\u0000\u0000\u0000\u014f\u014d\u0001\u0000"+
		"\u0000\u0000\u0150-\u0001\u0000\u0000\u0000\u0151\u0152\u0005\u0010\u0000"+
		"\u0000\u0152\u0153\u0005\u001f\u0000\u0000\u0153\u0154\u0003D\"\u0000"+
		"\u0154\u0155\u0005 \u0000\u0000\u0155\u0156\u0003\"\u0011\u0000\u0156"+
		"/\u0001\u0000\u0000\u0000\u0157\u0158\u0005\u0011\u0000\u0000\u0158\u015a"+
		"\u0005\u001f\u0000\u0000\u0159\u015b\u00032\u0019\u0000\u015a\u0159\u0001"+
		"\u0000\u0000\u0000\u015a\u015b\u0001\u0000\u0000\u0000\u015b\u015c\u0001"+
		"\u0000\u0000\u0000\u015c\u015e\u0005#\u0000\u0000\u015d\u015f\u00034\u001a"+
		"\u0000\u015e\u015d\u0001\u0000\u0000\u0000\u015e\u015f\u0001\u0000\u0000"+
		"\u0000\u015f\u0160\u0001\u0000\u0000\u0000\u0160\u0162\u0005#\u0000\u0000"+
		"\u0161\u0163\u00036\u001b\u0000\u0162\u0161\u0001\u0000\u0000\u0000\u0162"+
		"\u0163\u0001\u0000\u0000\u0000\u0163\u0164\u0001\u0000\u0000\u0000\u0164"+
		"\u0165\u0005 \u0000\u0000\u0165\u0166\u0003\"\u0011\u0000\u01661\u0001"+
		"\u0000\u0000\u0000\u0167\u016a\u00038\u001c\u0000\u0168\u016a\u0003:\u001d"+
		"\u0000\u0169\u0167\u0001\u0000\u0000\u0000\u0169\u0168\u0001\u0000\u0000"+
		"\u0000\u016a3\u0001\u0000\u0000\u0000\u016b\u016c\u0003D\"\u0000\u016c"+
		"5\u0001\u0000\u0000\u0000\u016d\u016e\u0003:\u001d\u0000\u016e7\u0001"+
		"\u0000\u0000\u0000\u016f\u0170\u0003(\u0014\u0000\u0170\u0173\u0005\u0019"+
		"\u0000\u0000\u0171\u0172\u0005%\u0000\u0000\u0172\u0174\u0003 \u0010\u0000"+
		"\u0173\u0171\u0001\u0000\u0000\u0000\u0173\u0174\u0001\u0000\u0000\u0000"+
		"\u0174\u0175\u0001\u0000\u0000\u0000\u0175\u0176\u0005$\u0000\u0000\u0176"+
		"\u0177\u0003D\"\u0000\u01779\u0001\u0000\u0000\u0000\u0178\u017b\u0003"+
		"D\"\u0000\u0179\u017a\u0005$\u0000\u0000\u017a\u017c\u0003D\"\u0000\u017b"+
		"\u0179\u0001\u0000\u0000\u0000\u017b\u017c\u0001\u0000\u0000\u0000\u017c"+
		";\u0001\u0000\u0000\u0000\u017d\u017e\u0005\u0012\u0000\u0000\u017e\u017f"+
		"\u0005#\u0000\u0000\u017f=\u0001\u0000\u0000\u0000\u0180\u0181\u0005\u0013"+
		"\u0000\u0000\u0181\u0182\u0005#\u0000\u0000\u0182?\u0001\u0000\u0000\u0000"+
		"\u0183\u0185\u0005\u0014\u0000\u0000\u0184\u0186\u0003D\"\u0000\u0185"+
		"\u0184\u0001\u0000\u0000\u0000\u0185\u0186\u0001\u0000\u0000\u0000\u0186"+
		"\u0187\u0001\u0000\u0000\u0000\u0187\u0188\u0005#\u0000\u0000\u0188A\u0001"+
		"\u0000\u0000\u0000\u0189\u018c\u0003D\"\u0000\u018a\u018b\u0005$\u0000"+
		"\u0000\u018b\u018d\u0003D\"\u0000\u018c\u018a\u0001\u0000\u0000\u0000"+
		"\u018c\u018d\u0001\u0000\u0000\u0000\u018d\u018e\u0001\u0000\u0000\u0000"+
		"\u018e\u018f\u0005#\u0000\u0000\u018fC\u0001\u0000\u0000\u0000\u0190\u0191"+
		"\u0003F#\u0000\u0191E\u0001\u0000\u0000\u0000\u0192\u0197\u0003H$\u0000"+
		"\u0193\u0194\u0005)\u0000\u0000\u0194\u0196\u0003H$\u0000\u0195\u0193"+
		"\u0001\u0000\u0000\u0000\u0196\u0199\u0001\u0000\u0000\u0000\u0197\u0195"+
		"\u0001\u0000\u0000\u0000\u0197\u0198\u0001\u0000\u0000\u0000\u0198G\u0001"+
		"\u0000\u0000\u0000\u0199\u0197\u0001\u0000\u0000\u0000\u019a\u019f\u0003"+
		"J%\u0000\u019b\u019c\u00059\u0000\u0000\u019c\u019e\u0003J%\u0000\u019d"+
		"\u019b\u0001\u0000\u0000\u0000\u019e\u01a1\u0001\u0000\u0000\u0000\u019f"+
		"\u019d\u0001\u0000\u0000\u0000\u019f\u01a0\u0001\u0000\u0000\u0000\u01a0"+
		"I\u0001\u0000\u0000\u0000\u01a1\u019f\u0001\u0000\u0000\u0000\u01a2\u01a7"+
		"\u0003L&\u0000\u01a3\u01a4\u00058\u0000\u0000\u01a4\u01a6\u0003L&\u0000"+
		"\u01a5\u01a3\u0001\u0000\u0000\u0000\u01a6\u01a9\u0001\u0000\u0000\u0000"+
		"\u01a7\u01a5\u0001\u0000\u0000\u0000\u01a7\u01a8\u0001\u0000\u0000\u0000"+
		"\u01a8K\u0001\u0000\u0000\u0000\u01a9\u01a7\u0001\u0000\u0000\u0000\u01aa"+
		"\u01af\u0003N\'\u0000\u01ab\u01ac\u0007\u0002\u0000\u0000\u01ac\u01ae"+
		"\u0003N\'\u0000\u01ad\u01ab\u0001\u0000\u0000\u0000\u01ae\u01b1\u0001"+
		"\u0000\u0000\u0000\u01af\u01ad\u0001\u0000\u0000\u0000\u01af\u01b0\u0001"+
		"\u0000\u0000\u0000\u01b0M\u0001\u0000\u0000\u0000\u01b1\u01af\u0001\u0000"+
		"\u0000\u0000\u01b2\u01b6\u0003R)\u0000\u01b3\u01b5\u0003P(\u0000\u01b4"+
		"\u01b3\u0001\u0000\u0000\u0000\u01b5\u01b8\u0001\u0000\u0000\u0000\u01b6"+
		"\u01b4\u0001\u0000\u0000\u0000\u01b6\u01b7\u0001\u0000\u0000\u0000\u01b7"+
		"O\u0001\u0000\u0000\u0000\u01b8\u01b6\u0001\u0000\u0000\u0000\u01b9\u01ba"+
		"\u0007\u0003\u0000\u0000\u01ba\u01c0\u0003R)\u0000\u01bb\u01bc\u0005\u0016"+
		"\u0000\u0000\u01bc\u01c0\u0003 \u0010\u0000\u01bd\u01be\u0005\u0017\u0000"+
		"\u0000\u01be\u01c0\u0003 \u0010\u0000\u01bf\u01b9\u0001\u0000\u0000\u0000"+
		"\u01bf\u01bb\u0001\u0000\u0000\u0000\u01bf\u01bd\u0001\u0000\u0000\u0000"+
		"\u01c0Q\u0001\u0000\u0000\u0000\u01c1\u01c6\u0003T*\u0000\u01c2\u01c3"+
		"\u0007\u0004\u0000\u0000\u01c3\u01c5\u0003T*\u0000\u01c4\u01c2\u0001\u0000"+
		"\u0000\u0000\u01c5\u01c8\u0001\u0000\u0000\u0000\u01c6\u01c4\u0001\u0000"+
		"\u0000\u0000\u01c6\u01c7\u0001\u0000\u0000\u0000\u01c7S\u0001\u0000\u0000"+
		"\u0000\u01c8\u01c6\u0001\u0000\u0000\u0000\u01c9\u01ce\u0003V+\u0000\u01ca"+
		"\u01cb\u0007\u0005\u0000\u0000\u01cb\u01cd\u0003V+\u0000\u01cc\u01ca\u0001"+
		"\u0000\u0000\u0000\u01cd\u01d0\u0001\u0000\u0000\u0000\u01ce\u01cc\u0001"+
		"\u0000\u0000\u0000\u01ce\u01cf\u0001\u0000\u0000\u0000\u01cfU\u0001\u0000"+
		"\u0000\u0000\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d1\u01d2\u0007\u0006"+
		"\u0000\u0000\u01d2\u01d5\u0003V+\u0000\u01d3\u01d5\u0003X,\u0000\u01d4"+
		"\u01d1\u0001\u0000\u0000\u0000\u01d4\u01d3\u0001\u0000\u0000\u0000\u01d5"+
		"W\u0001\u0000\u0000\u0000\u01d6\u01da\u0003Z-\u0000\u01d7\u01d9\u0003"+
		"d2\u0000\u01d8\u01d7\u0001\u0000\u0000\u0000\u01d9\u01dc\u0001\u0000\u0000"+
		"\u0000\u01da\u01d8\u0001\u0000\u0000\u0000\u01da\u01db\u0001\u0000\u0000"+
		"\u0000\u01dbY\u0001\u0000\u0000\u0000\u01dc\u01da\u0001\u0000\u0000\u0000"+
		"\u01dd\u01e3\u0003l6\u0000\u01de\u01e3\u0003\\.\u0000\u01df\u01e3\u0003"+
		"^/\u0000\u01e0\u01e3\u0003`0\u0000\u01e1\u01e3\u0003b1\u0000\u01e2\u01dd"+
		"\u0001\u0000\u0000\u0000\u01e2\u01de\u0001\u0000\u0000\u0000\u01e2\u01df"+
		"\u0001\u0000\u0000\u0000\u01e2\u01e0\u0001\u0000\u0000\u0000\u01e2\u01e1"+
		"\u0001\u0000\u0000\u0000\u01e3[\u0001\u0000\u0000\u0000\u01e4\u01e5\u0005"+
		"\u001f\u0000\u0000\u01e5\u01e6\u0003D\"\u0000\u01e6\u01e7\u0005 \u0000"+
		"\u0000\u01e7]\u0001\u0000\u0000\u0000\u01e8\u01e9\u0005\n\u0000\u0000"+
		"\u01e9_\u0001\u0000\u0000\u0000\u01ea\u01eb\u0005\u000b\u0000\u0000\u01eb"+
		"a\u0001\u0000\u0000\u0000\u01ec\u01ed\u0005\u0019\u0000\u0000\u01edc\u0001"+
		"\u0000\u0000\u0000\u01ee\u01f1\u0003f3\u0000\u01ef\u01f1\u0003h4\u0000"+
		"\u01f0\u01ee\u0001\u0000\u0000\u0000\u01f0\u01ef\u0001\u0000\u0000\u0000"+
		"\u01f1e\u0001\u0000\u0000\u0000\u01f2\u01f3\u0007\u0007\u0000\u0000\u01f3"+
		"\u01f4\u0005\u0019\u0000\u0000\u01f4g\u0001\u0000\u0000\u0000\u01f5\u01f7"+
		"\u0005\u001f\u0000\u0000\u01f6\u01f8\u0003j5\u0000\u01f7\u01f6\u0001\u0000"+
		"\u0000\u0000\u01f7\u01f8\u0001\u0000\u0000\u0000\u01f8\u01f9\u0001\u0000"+
		"\u0000\u0000\u01f9\u01fa\u0005 \u0000\u0000\u01fai\u0001\u0000\u0000\u0000"+
		"\u01fb\u0200\u0003D\"\u0000\u01fc\u01fd\u0005&\u0000\u0000\u01fd\u01ff"+
		"\u0003D\"\u0000\u01fe\u01fc\u0001\u0000\u0000\u0000\u01ff\u0202\u0001"+
		"\u0000\u0000\u0000\u0200\u01fe\u0001\u0000\u0000\u0000\u0200\u0201\u0001"+
		"\u0000\u0000\u0000\u0201k\u0001\u0000\u0000\u0000\u0202\u0200\u0001\u0000"+
		"\u0000\u0000\u0203\u020c\u0003n7\u0000\u0204\u020c\u0003p8\u0000\u0205"+
		"\u020c\u0003r9\u0000\u0206\u020c\u0003t:\u0000\u0207\u020c\u0003v;\u0000"+
		"\u0208\u020c\u0003x<\u0000\u0209\u020c\u0003z=\u0000\u020a\u020c\u0003"+
		"|>\u0000\u020b\u0203\u0001\u0000\u0000\u0000\u020b\u0204\u0001\u0000\u0000"+
		"\u0000\u020b\u0205\u0001\u0000\u0000\u0000\u020b\u0206\u0001\u0000\u0000"+
		"\u0000\u020b\u0207\u0001\u0000\u0000\u0000\u020b\u0208\u0001\u0000\u0000"+
		"\u0000\u020b\u0209\u0001\u0000\u0000\u0000\u020b\u020a\u0001\u0000\u0000"+
		"\u0000\u020cm\u0001\u0000\u0000\u0000\u020d\u020e\u0005\u001a\u0000\u0000"+
		"\u020eo\u0001\u0000\u0000\u0000\u020f\u0210\u0005\u001b\u0000\u0000\u0210"+
		"q\u0001\u0000\u0000\u0000\u0211\u0212\u0005\u001c\u0000\u0000\u0212s\u0001"+
		"\u0000\u0000\u0000\u0213\u0214\u0005\u0018\u0000\u0000\u0214u\u0001\u0000"+
		"\u0000\u0000\u0215\u0216\u0005\u001d\u0000\u0000\u0216w\u0001\u0000\u0000"+
		"\u0000\u0217\u0218\u0005\u001e\u0000\u0000\u0218y\u0001\u0000\u0000\u0000"+
		"\u0219\u021a\u0005>\u0000\u0000\u021a{\u0001\u0000\u0000\u0000\u021b\u021c"+
		"\u0005\u0015\u0000\u0000\u021c}\u0001\u0000\u0000\u00004\u0082\u0084\u008d"+
		"\u0095\u009b\u009f\u00a4\u00a6\u00af\u00b4\u00b6\u00bd\u00c3\u00ce\u00da"+
		"\u00e1\u00ea\u00f1\u00f8\u0107\u010e\u0118\u0121\u0126\u0128\u0135\u013b"+
		"\u0149\u014f\u015a\u015e\u0162\u0169\u0173\u017b\u0185\u018c\u0197\u019f"+
		"\u01a7\u01af\u01b6\u01bf\u01c6\u01ce\u01d4\u01da\u01e2\u01f0\u01f7\u0200"+
		"\u020b";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
