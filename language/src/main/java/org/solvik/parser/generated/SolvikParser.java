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
		FUN=1, CLASS=2, INTERFACE=3, ENUM=4, SEALED=5, DELEGATE=6, IMPLEMENTS=7, 
		OPEN=8, EXTENDS=9, OVERRIDE=10, INIT=11, THIS=12, SUPER=13, VAL=14, VAR=15, 
		IF=16, ELSE=17, WHILE=18, FOR=19, BREAK=20, CONTINUE=21, RETURN=22, MATCH=23, 
		ARROW=24, SWITCH=25, CASE=26, DEFAULT=27, REGEX_KW=28, NULL=29, IS=30, 
		AS=31, BOOL_LITERAL=32, Identifier=33, INT_LITERAL=34, LONG_LITERAL=35, 
		FLOATING_LITERAL=36, CHAR_LITERAL=37, STRING_LITERAL=38, LPAREN=39, RPAREN=40, 
		LBRACE=41, RBRACE=42, SEMI=43, ASSIGN=44, COLON=45, COMMA=46, DOT=47, 
		NULLABLE_DOT=48, NULL_COALESCE=49, QUESTION=50, LBRACKET=51, RBRACKET=52, 
		ADD=53, SUB=54, MUL=55, DIV=56, BANG=57, EQ=58, NEQ=59, LT=60, LE=61, 
		GT=62, GE=63, AND=64, OR=65, WS=66, NEWLINE=67, LINE_COMMENT=68, BLOCK_COMMENT=69, 
		RAW_STRING_LITERAL=70;
	public static final int
		RULE_compilationUnit = 0, RULE_functionDecl = 1, RULE_classDecl = 2, RULE_interfaceDecl = 3, 
		RULE_enumDecl = 4, RULE_enumVariant = 5, RULE_interfaceMember = 6, RULE_signatureDecl = 7, 
		RULE_defaultMethodDecl = 8, RULE_typeRefList = 9, RULE_classMember = 10, 
		RULE_delegateDecl = 11, RULE_methodDecl = 12, RULE_methodModifier = 13, 
		RULE_propertyDecl = 14, RULE_initDecl = 15, RULE_parameterList = 16, RULE_parameter = 17, 
		RULE_typeParameterList = 18, RULE_typeRef = 19, RULE_typeArguments = 20, 
		RULE_block = 21, RULE_statement = 22, RULE_localDecl = 23, RULE_bindingKind = 24, 
		RULE_ifStmt = 25, RULE_elseBranch = 26, RULE_whileStmt = 27, RULE_forStmt = 28, 
		RULE_forInit = 29, RULE_forCondition = 30, RULE_forUpdate = 31, RULE_switchStmt = 32, 
		RULE_switchCase = 33, RULE_defaultCase = 34, RULE_caseLabel = 35, RULE_regexCaseLabel = 36, 
		RULE_localDeclNoSemi = 37, RULE_assignable = 38, RULE_breakStmt = 39, 
		RULE_continueStmt = 40, RULE_returnStmt = 41, RULE_exprStmt = 42, RULE_expression = 43, 
		RULE_nullCoalescing = 44, RULE_logicalOr = 45, RULE_logicalAnd = 46, RULE_equality = 47, 
		RULE_relational = 48, RULE_relation = 49, RULE_additive = 50, RULE_multiplicative = 51, 
		RULE_unary = 52, RULE_postfix = 53, RULE_primary = 54, RULE_matchExpr = 55, 
		RULE_matchBranch = 56, RULE_pattern = 57, RULE_patternList = 58, RULE_paren = 59, 
		RULE_thisExpr = 60, RULE_superExpr = 61, RULE_name = 62, RULE_suffix = 63, 
		RULE_memberSuffix = 64, RULE_callSuffix = 65, RULE_argumentList = 66, 
		RULE_literal = 67, RULE_intLiteral = 68, RULE_longLiteral = 69, RULE_floatingLiteral = 70, 
		RULE_boolLiteral = 71, RULE_charLiteral = 72, RULE_stringLiteral = 73, 
		RULE_rawStringLiteral = 74, RULE_nullLiteral = 75;
	private static String[] makeRuleNames() {
		return new String[] {
			"compilationUnit", "functionDecl", "classDecl", "interfaceDecl", "enumDecl", 
			"enumVariant", "interfaceMember", "signatureDecl", "defaultMethodDecl", 
			"typeRefList", "classMember", "delegateDecl", "methodDecl", "methodModifier", 
			"propertyDecl", "initDecl", "parameterList", "parameter", "typeParameterList", 
			"typeRef", "typeArguments", "block", "statement", "localDecl", "bindingKind", 
			"ifStmt", "elseBranch", "whileStmt", "forStmt", "forInit", "forCondition", 
			"forUpdate", "switchStmt", "switchCase", "defaultCase", "caseLabel", 
			"regexCaseLabel", "localDeclNoSemi", "assignable", "breakStmt", "continueStmt", 
			"returnStmt", "exprStmt", "expression", "nullCoalescing", "logicalOr", 
			"logicalAnd", "equality", "relational", "relation", "additive", "multiplicative", 
			"unary", "postfix", "primary", "matchExpr", "matchBranch", "pattern", 
			"patternList", "paren", "thisExpr", "superExpr", "name", "suffix", "memberSuffix", 
			"callSuffix", "argumentList", "literal", "intLiteral", "longLiteral", 
			"floatingLiteral", "boolLiteral", "charLiteral", "stringLiteral", "rawStringLiteral", 
			"nullLiteral"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'fun'", "'class'", "'interface'", "'enum'", "'sealed'", "'delegate'", 
			"'implements'", "'open'", "'extends'", "'override'", "'init'", "'this'", 
			"'super'", "'val'", "'var'", "'if'", "'else'", "'while'", "'for'", "'break'", 
			"'continue'", "'return'", "'match'", "'=>'", "'switch'", "'case'", "'default'", 
			"'regex'", "'null'", "'is'", "'as'", null, null, null, null, null, null, 
			null, "'('", "')'", "'{'", "'}'", "';'", "'='", "':'", "','", "'.'", 
			"'?.'", "'??'", "'?'", "'['", "']'", "'+'", "'-'", "'*'", "'/'", "'!'", 
			"'=='", "'!='", "'<'", "'<='", "'>'", "'>='", "'&&'", "'||'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "FUN", "CLASS", "INTERFACE", "ENUM", "SEALED", "DELEGATE", "IMPLEMENTS", 
			"OPEN", "EXTENDS", "OVERRIDE", "INIT", "THIS", "SUPER", "VAL", "VAR", 
			"IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE", "RETURN", "MATCH", 
			"ARROW", "SWITCH", "CASE", "DEFAULT", "REGEX_KW", "NULL", "IS", "AS", 
			"BOOL_LITERAL", "Identifier", "INT_LITERAL", "LONG_LITERAL", "FLOATING_LITERAL", 
			"CHAR_LITERAL", "STRING_LITERAL", "LPAREN", "RPAREN", "LBRACE", "RBRACE", 
			"SEMI", "ASSIGN", "COLON", "COMMA", "DOT", "NULLABLE_DOT", "NULL_COALESCE", 
			"QUESTION", "LBRACKET", "RBRACKET", "ADD", "SUB", "MUL", "DIV", "BANG", 
			"EQ", "NEQ", "LT", "LE", "GT", "GE", "AND", "OR", "WS", "NEWLINE", "LINE_COMMENT", 
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
		public List<EnumDeclContext> enumDecl() {
			return getRuleContexts(EnumDeclContext.class);
		}
		public EnumDeclContext enumDecl(int i) {
			return getRuleContext(EnumDeclContext.class,i);
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
			setState(159);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 8796093022526L) != 0)) {
				{
				setState(157);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
					{
					setState(152);
					functionDecl();
					}
					break;
				case CLASS:
				case SEALED:
				case OPEN:
					{
					setState(153);
					classDecl();
					}
					break;
				case INTERFACE:
					{
					setState(154);
					interfaceDecl();
					}
					break;
				case ENUM:
					{
					setState(155);
					enumDecl();
					}
					break;
				case SEMI:
					{
					setState(156);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(161);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(162);
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
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
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
			setState(164);
			match(FUN);
			setState(165);
			match(Identifier);
			setState(167);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(166);
				typeParameterList();
				}
			}

			setState(169);
			match(LPAREN);
			setState(171);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(170);
				parameterList();
				}
			}

			setState(173);
			match(RPAREN);
			setState(174);
			match(COLON);
			setState(175);
			typeRef();
			setState(176);
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
		public TerminalNode SEALED() { return getToken(SolvikParser.SEALED, 0); }
		public TerminalNode OPEN() { return getToken(SolvikParser.OPEN, 0); }
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
		}
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
			setState(179);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEALED) {
				{
				setState(178);
				match(SEALED);
				}
			}

			setState(182);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN) {
				{
				setState(181);
				match(OPEN);
				}
			}

			setState(184);
			match(CLASS);
			setState(185);
			match(Identifier);
			setState(187);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(186);
				typeParameterList();
				}
			}

			setState(191);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(189);
				match(EXTENDS);
				setState(190);
				typeRef();
				}
			}

			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IMPLEMENTS) {
				{
				setState(193);
				match(IMPLEMENTS);
				setState(194);
				typeRefList();
				}
			}

			setState(197);
			match(LBRACE);
			setState(202);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 8796093074754L) != 0)) {
				{
				setState(200);
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
					setState(198);
					classMember();
					}
					break;
				case SEMI:
					{
					setState(199);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(204);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(205);
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
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
		}
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
			setState(207);
			match(INTERFACE);
			setState(208);
			match(Identifier);
			setState(210);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(209);
				typeParameterList();
				}
			}

			setState(214);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(212);
				match(EXTENDS);
				setState(213);
				typeRefList();
				}
			}

			setState(216);
			match(LBRACE);
			setState(221);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==FUN || _la==SEMI) {
				{
				setState(219);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUN:
					{
					setState(217);
					interfaceMember();
					}
					break;
				case SEMI:
					{
					setState(218);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(223);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(224);
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
	public static class EnumDeclContext extends ParserRuleContext {
		public TerminalNode ENUM() { return getToken(SolvikParser.ENUM, 0); }
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LBRACE() { return getToken(SolvikParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(SolvikParser.RBRACE, 0); }
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
		}
		public List<EnumVariantContext> enumVariant() {
			return getRuleContexts(EnumVariantContext.class);
		}
		public EnumVariantContext enumVariant(int i) {
			return getRuleContext(EnumVariantContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public EnumDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitEnumDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumDeclContext enumDecl() throws RecognitionException {
		EnumDeclContext _localctx = new EnumDeclContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_enumDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(226);
			match(ENUM);
			setState(227);
			match(Identifier);
			setState(229);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(228);
				typeParameterList();
				}
			}

			setState(231);
			match(LBRACE);
			setState(236);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Identifier || _la==SEMI) {
				{
				setState(234);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case Identifier:
					{
					setState(232);
					enumVariant();
					}
					break;
				case SEMI:
					{
					setState(233);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(238);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(239);
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
	public static class EnumVariantContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode SEMI() { return getToken(SolvikParser.SEMI, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public TypeRefListContext typeRefList() {
			return getRuleContext(TypeRefListContext.class,0);
		}
		public EnumVariantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumVariant; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitEnumVariant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumVariantContext enumVariant() throws RecognitionException {
		EnumVariantContext _localctx = new EnumVariantContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_enumVariant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(241);
			match(Identifier);
			setState(247);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(242);
				match(LPAREN);
				setState(244);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Identifier) {
					{
					setState(243);
					typeRefList();
					}
				}

				setState(246);
				match(RPAREN);
				}
			}

			setState(249);
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
		enterRule(_localctx, 12, RULE_interfaceMember);
		try {
			setState(253);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(251);
				signatureDecl();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(252);
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
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
		}
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
		enterRule(_localctx, 14, RULE_signatureDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(255);
			match(FUN);
			setState(256);
			match(Identifier);
			setState(258);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(257);
				typeParameterList();
				}
			}

			setState(260);
			match(LPAREN);
			setState(262);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(261);
				parameterList();
				}
			}

			setState(264);
			match(RPAREN);
			setState(265);
			match(COLON);
			setState(266);
			typeRef();
			setState(267);
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
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
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
		enterRule(_localctx, 16, RULE_defaultMethodDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			match(FUN);
			setState(270);
			match(Identifier);
			setState(272);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(271);
				typeParameterList();
				}
			}

			setState(274);
			match(LPAREN);
			setState(276);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(275);
				parameterList();
				}
			}

			setState(278);
			match(RPAREN);
			setState(279);
			match(COLON);
			setState(280);
			typeRef();
			setState(281);
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
		enterRule(_localctx, 18, RULE_typeRefList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(283);
			typeRef();
			setState(288);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(284);
				match(COMMA);
				setState(285);
				typeRef();
				}
				}
				setState(290);
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
		enterRule(_localctx, 20, RULE_classMember);
		try {
			setState(295);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(291);
				propertyDecl();
				}
				break;
			case DELEGATE:
				enterOuterAlt(_localctx, 2);
				{
				setState(292);
				delegateDecl();
				}
				break;
			case INIT:
				enterOuterAlt(_localctx, 3);
				{
				setState(293);
				initDecl();
				}
				break;
			case FUN:
			case OPEN:
			case OVERRIDE:
				enterOuterAlt(_localctx, 4);
				{
				setState(294);
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
		enterRule(_localctx, 22, RULE_delegateDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(297);
			match(DELEGATE);
			setState(298);
			match(VAL);
			setState(299);
			match(Identifier);
			setState(300);
			match(COLON);
			setState(301);
			typeRef();
			setState(304);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(302);
				match(ASSIGN);
				setState(303);
				expression();
				}
			}

			setState(306);
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
		public TypeParameterListContext typeParameterList() {
			return getRuleContext(TypeParameterListContext.class,0);
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
		enterRule(_localctx, 24, RULE_methodDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(311);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OPEN || _la==OVERRIDE) {
				{
				{
				setState(308);
				methodModifier();
				}
				}
				setState(313);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(314);
			match(FUN);
			setState(315);
			match(Identifier);
			setState(317);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(316);
				typeParameterList();
				}
			}

			setState(319);
			match(LPAREN);
			setState(321);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(320);
				parameterList();
				}
			}

			setState(323);
			match(RPAREN);
			setState(324);
			match(COLON);
			setState(325);
			typeRef();
			setState(326);
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
		enterRule(_localctx, 26, RULE_methodModifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(328);
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
		enterRule(_localctx, 28, RULE_propertyDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(330);
			bindingKind();
			setState(331);
			match(Identifier);
			setState(332);
			match(COLON);
			setState(333);
			typeRef();
			setState(336);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(334);
				match(ASSIGN);
				setState(335);
				expression();
				}
			}

			setState(338);
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
		enterRule(_localctx, 30, RULE_initDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(340);
			match(INIT);
			setState(341);
			match(LPAREN);
			setState(343);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(342);
				parameterList();
				}
			}

			setState(345);
			match(RPAREN);
			setState(346);
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
		enterRule(_localctx, 32, RULE_parameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(348);
			parameter();
			setState(353);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(349);
				match(COMMA);
				setState(350);
				parameter();
				}
				}
				setState(355);
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
		enterRule(_localctx, 34, RULE_parameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(356);
			match(Identifier);
			setState(357);
			match(COLON);
			setState(358);
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
	public static class TypeParameterListContext extends ParserRuleContext {
		public TerminalNode LT() { return getToken(SolvikParser.LT, 0); }
		public List<TerminalNode> Identifier() { return getTokens(SolvikParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(SolvikParser.Identifier, i);
		}
		public TerminalNode GT() { return getToken(SolvikParser.GT, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
		public TypeParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeParameterList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitTypeParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeParameterListContext typeParameterList() throws RecognitionException {
		TypeParameterListContext _localctx = new TypeParameterListContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_typeParameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(360);
			match(LT);
			setState(361);
			match(Identifier);
			setState(366);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(362);
				match(COMMA);
				setState(363);
				match(Identifier);
				}
				}
				setState(368);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(369);
			match(GT);
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
		public TypeArgumentsContext typeArguments() {
			return getRuleContext(TypeArgumentsContext.class,0);
		}
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
		enterRule(_localctx, 38, RULE_typeRef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(371);
			match(Identifier);
			setState(373);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				{
				setState(372);
				typeArguments();
				}
				break;
			}
			setState(376);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==QUESTION) {
				{
				setState(375);
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
	public static class TypeArgumentsContext extends ParserRuleContext {
		public TerminalNode LT() { return getToken(SolvikParser.LT, 0); }
		public List<TypeRefContext> typeRef() {
			return getRuleContexts(TypeRefContext.class);
		}
		public TypeRefContext typeRef(int i) {
			return getRuleContext(TypeRefContext.class,i);
		}
		public TerminalNode GT() { return getToken(SolvikParser.GT, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
		public TypeArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeArguments; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitTypeArguments(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeArgumentsContext typeArguments() throws RecognitionException {
		TypeArgumentsContext _localctx = new TypeArgumentsContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_typeArguments);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(378);
			match(LT);
			setState(379);
			typeRef();
			setState(384);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(380);
				match(COMMA);
				setState(381);
				typeRef();
				}
				}
				setState(386);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(387);
			match(GT);
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
		enterRule(_localctx, 42, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(389);
			match(LBRACE);
			setState(394);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((((_la - 12)) & ~0x3f) == 0 && ((1L << (_la - 12)) & 288269960985325535L) != 0)) {
				{
				setState(392);
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
				case MATCH:
				case SWITCH:
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
					setState(390);
					statement();
					}
					break;
				case SEMI:
					{
					setState(391);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(396);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(397);
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
		public SwitchStmtContext switchStmt() {
			return getRuleContext(SwitchStmtContext.class,0);
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
		enterRule(_localctx, 44, RULE_statement);
		try {
			setState(408);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(399);
				localDecl();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 2);
				{
				setState(400);
				ifStmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 3);
				{
				setState(401);
				whileStmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 4);
				{
				setState(402);
				forStmt();
				}
				break;
			case SWITCH:
				enterOuterAlt(_localctx, 5);
				{
				setState(403);
				switchStmt();
				}
				break;
			case BREAK:
				enterOuterAlt(_localctx, 6);
				{
				setState(404);
				breakStmt();
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 7);
				{
				setState(405);
				continueStmt();
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 8);
				{
				setState(406);
				returnStmt();
				}
				break;
			case THIS:
			case SUPER:
			case MATCH:
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
				enterOuterAlt(_localctx, 9);
				{
				setState(407);
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
		enterRule(_localctx, 46, RULE_localDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(410);
			bindingKind();
			setState(411);
			match(Identifier);
			setState(414);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(412);
				match(COLON);
				setState(413);
				typeRef();
				}
			}

			setState(416);
			match(ASSIGN);
			setState(417);
			expression();
			setState(418);
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
		enterRule(_localctx, 48, RULE_bindingKind);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(420);
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
		enterRule(_localctx, 50, RULE_ifStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(422);
			match(IF);
			setState(423);
			match(LPAREN);
			setState(424);
			expression();
			setState(425);
			match(RPAREN);
			setState(426);
			block();
			setState(428);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(427);
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
		enterRule(_localctx, 52, RULE_elseBranch);
		try {
			setState(434);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(430);
				match(ELSE);
				setState(431);
				ifStmt();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(432);
				match(ELSE);
				setState(433);
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
		enterRule(_localctx, 54, RULE_whileStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(436);
			match(WHILE);
			setState(437);
			match(LPAREN);
			setState(438);
			expression();
			setState(439);
			match(RPAREN);
			setState(440);
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
		enterRule(_localctx, 56, RULE_forStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(442);
			match(FOR);
			setState(443);
			match(LPAREN);
			setState(445);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 12)) & ~0x3f) == 0 && ((1L << (_la - 12)) & 288269958837831695L) != 0)) {
				{
				setState(444);
				forInit();
				}
			}

			setState(447);
			match(SEMI);
			setState(449);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 12)) & ~0x3f) == 0 && ((1L << (_la - 12)) & 288269958837831683L) != 0)) {
				{
				setState(448);
				forCondition();
				}
			}

			setState(451);
			match(SEMI);
			setState(453);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 12)) & ~0x3f) == 0 && ((1L << (_la - 12)) & 288269958837831683L) != 0)) {
				{
				setState(452);
				forUpdate();
				}
			}

			setState(455);
			match(RPAREN);
			setState(456);
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
		enterRule(_localctx, 58, RULE_forInit);
		try {
			setState(460);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(458);
				localDeclNoSemi();
				}
				break;
			case THIS:
			case SUPER:
			case MATCH:
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
				setState(459);
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
		enterRule(_localctx, 60, RULE_forCondition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(462);
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
		enterRule(_localctx, 62, RULE_forUpdate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(464);
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
	public static class SwitchStmtContext extends ParserRuleContext {
		public TerminalNode SWITCH() { return getToken(SolvikParser.SWITCH, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public TerminalNode LBRACE() { return getToken(SolvikParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(SolvikParser.RBRACE, 0); }
		public List<SwitchCaseContext> switchCase() {
			return getRuleContexts(SwitchCaseContext.class);
		}
		public SwitchCaseContext switchCase(int i) {
			return getRuleContext(SwitchCaseContext.class,i);
		}
		public List<DefaultCaseContext> defaultCase() {
			return getRuleContexts(DefaultCaseContext.class);
		}
		public DefaultCaseContext defaultCase(int i) {
			return getRuleContext(DefaultCaseContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public SwitchStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switchStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitSwitchStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SwitchStmtContext switchStmt() throws RecognitionException {
		SwitchStmtContext _localctx = new SwitchStmtContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_switchStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(466);
			match(SWITCH);
			setState(467);
			match(LPAREN);
			setState(468);
			expression();
			setState(469);
			match(RPAREN);
			setState(470);
			match(LBRACE);
			setState(476);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 8796294348800L) != 0)) {
				{
				setState(474);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case CASE:
					{
					setState(471);
					switchCase();
					}
					break;
				case DEFAULT:
					{
					setState(472);
					defaultCase();
					}
					break;
				case SEMI:
					{
					setState(473);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(478);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(479);
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
	public static class SwitchCaseContext extends ParserRuleContext {
		public TerminalNode CASE() { return getToken(SolvikParser.CASE, 0); }
		public List<CaseLabelContext> caseLabel() {
			return getRuleContexts(CaseLabelContext.class);
		}
		public CaseLabelContext caseLabel(int i) {
			return getRuleContext(CaseLabelContext.class,i);
		}
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
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
		public SwitchCaseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switchCase; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitSwitchCase(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SwitchCaseContext switchCase() throws RecognitionException {
		SwitchCaseContext _localctx = new SwitchCaseContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_switchCase);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(481);
			match(CASE);
			setState(482);
			caseLabel();
			setState(487);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(483);
				match(COMMA);
				setState(484);
				caseLabel();
				}
				}
				setState(489);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(490);
			match(COLON);
			setState(495);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(493);
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
					case MATCH:
					case SWITCH:
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
						setState(491);
						statement();
						}
						break;
					case SEMI:
						{
						setState(492);
						match(SEMI);
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					} 
				}
				setState(497);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
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
	public static class DefaultCaseContext extends ParserRuleContext {
		public TerminalNode DEFAULT() { return getToken(SolvikParser.DEFAULT, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
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
		public DefaultCaseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_defaultCase; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitDefaultCase(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DefaultCaseContext defaultCase() throws RecognitionException {
		DefaultCaseContext _localctx = new DefaultCaseContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_defaultCase);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(498);
			match(DEFAULT);
			setState(499);
			match(COLON);
			setState(504);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(502);
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
					case MATCH:
					case SWITCH:
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
						setState(500);
						statement();
						}
						break;
					case SEMI:
						{
						setState(501);
						match(SEMI);
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					} 
				}
				setState(506);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
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
	public static class CaseLabelContext extends ParserRuleContext {
		public RegexCaseLabelContext regexCaseLabel() {
			return getRuleContext(RegexCaseLabelContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public CaseLabelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_caseLabel; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitCaseLabel(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CaseLabelContext caseLabel() throws RecognitionException {
		CaseLabelContext _localctx = new CaseLabelContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_caseLabel);
		try {
			setState(509);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case REGEX_KW:
				enterOuterAlt(_localctx, 1);
				{
				setState(507);
				regexCaseLabel();
				}
				break;
			case THIS:
			case SUPER:
			case MATCH:
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
				setState(508);
				expression();
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
	public static class RegexCaseLabelContext extends ParserRuleContext {
		public TerminalNode REGEX_KW() { return getToken(SolvikParser.REGEX_KW, 0); }
		public StringLiteralContext stringLiteral() {
			return getRuleContext(StringLiteralContext.class,0);
		}
		public RawStringLiteralContext rawStringLiteral() {
			return getRuleContext(RawStringLiteralContext.class,0);
		}
		public RegexCaseLabelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_regexCaseLabel; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitRegexCaseLabel(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RegexCaseLabelContext regexCaseLabel() throws RecognitionException {
		RegexCaseLabelContext _localctx = new RegexCaseLabelContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_regexCaseLabel);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(511);
			match(REGEX_KW);
			setState(514);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING_LITERAL:
				{
				setState(512);
				stringLiteral();
				}
				break;
			case RAW_STRING_LITERAL:
				{
				setState(513);
				rawStringLiteral();
				}
				break;
			default:
				throw new NoViableAltException(this);
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
		enterRule(_localctx, 74, RULE_localDeclNoSemi);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(516);
			bindingKind();
			setState(517);
			match(Identifier);
			setState(520);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(518);
				match(COLON);
				setState(519);
				typeRef();
				}
			}

			setState(522);
			match(ASSIGN);
			setState(523);
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
		enterRule(_localctx, 76, RULE_assignable);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(525);
			expression();
			setState(528);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(526);
				match(ASSIGN);
				setState(527);
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
		enterRule(_localctx, 78, RULE_breakStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(530);
			match(BREAK);
			setState(531);
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
		enterRule(_localctx, 80, RULE_continueStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(533);
			match(CONTINUE);
			setState(534);
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
		enterRule(_localctx, 82, RULE_returnStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(536);
			match(RETURN);
			setState(538);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 12)) & ~0x3f) == 0 && ((1L << (_la - 12)) & 288269958837831683L) != 0)) {
				{
				setState(537);
				expression();
				}
			}

			setState(540);
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
		enterRule(_localctx, 84, RULE_exprStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(542);
			expression();
			setState(545);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(543);
				match(ASSIGN);
				setState(544);
				expression();
				}
			}

			setState(547);
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
		enterRule(_localctx, 86, RULE_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(549);
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
		enterRule(_localctx, 88, RULE_nullCoalescing);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(551);
			logicalOr();
			setState(556);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NULL_COALESCE) {
				{
				{
				setState(552);
				match(NULL_COALESCE);
				setState(553);
				logicalOr();
				}
				}
				setState(558);
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
		enterRule(_localctx, 90, RULE_logicalOr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(559);
			logicalAnd();
			setState(564);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(560);
				match(OR);
				setState(561);
				logicalAnd();
				}
				}
				setState(566);
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
		enterRule(_localctx, 92, RULE_logicalAnd);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(567);
			equality();
			setState(572);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(568);
				match(AND);
				setState(569);
				equality();
				}
				}
				setState(574);
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
		enterRule(_localctx, 94, RULE_equality);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(575);
			relational();
			setState(580);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EQ || _la==NEQ) {
				{
				{
				setState(576);
				_la = _input.LA(1);
				if ( !(_la==EQ || _la==NEQ) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(577);
				relational();
				}
				}
				setState(582);
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
		enterRule(_localctx, 96, RULE_relational);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(583);
			additive();
			setState(587);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & -1152921501385621504L) != 0)) {
				{
				{
				setState(584);
				relation();
				}
				}
				setState(589);
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
		enterRule(_localctx, 98, RULE_relation);
		int _la;
		try {
			setState(596);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LT:
			case LE:
			case GT:
			case GE:
				enterOuterAlt(_localctx, 1);
				{
				setState(590);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & -1152921504606846976L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(591);
				additive();
				}
				break;
			case IS:
				enterOuterAlt(_localctx, 2);
				{
				setState(592);
				match(IS);
				setState(593);
				typeRef();
				}
				break;
			case AS:
				enterOuterAlt(_localctx, 3);
				{
				setState(594);
				match(AS);
				setState(595);
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
		enterRule(_localctx, 100, RULE_additive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(598);
			multiplicative();
			setState(603);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==SUB) {
				{
				{
				setState(599);
				_la = _input.LA(1);
				if ( !(_la==ADD || _la==SUB) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(600);
				multiplicative();
				}
				}
				setState(605);
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
		enterRule(_localctx, 102, RULE_multiplicative);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(606);
			unary();
			setState(611);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==MUL || _la==DIV) {
				{
				{
				setState(607);
				_la = _input.LA(1);
				if ( !(_la==MUL || _la==DIV) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(608);
				unary();
				}
				}
				setState(613);
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
		enterRule(_localctx, 104, RULE_unary);
		int _la;
		try {
			setState(617);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SUB:
			case BANG:
				enterOuterAlt(_localctx, 1);
				{
				setState(614);
				_la = _input.LA(1);
				if ( !(_la==SUB || _la==BANG) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(615);
				unary();
				}
				break;
			case THIS:
			case SUPER:
			case MATCH:
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
				setState(616);
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
		enterRule(_localctx, 106, RULE_postfix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(619);
			primary();
			setState(623);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 422762220879872L) != 0)) {
				{
				{
				setState(620);
				suffix();
				}
				}
				setState(625);
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
		public MatchExprContext matchExpr() {
			return getRuleContext(MatchExprContext.class,0);
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
		enterRule(_localctx, 108, RULE_primary);
		try {
			setState(632);
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
				setState(626);
				literal();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(627);
				paren();
				}
				break;
			case THIS:
				enterOuterAlt(_localctx, 3);
				{
				setState(628);
				thisExpr();
				}
				break;
			case SUPER:
				enterOuterAlt(_localctx, 4);
				{
				setState(629);
				superExpr();
				}
				break;
			case MATCH:
				enterOuterAlt(_localctx, 5);
				{
				setState(630);
				matchExpr();
				}
				break;
			case Identifier:
				enterOuterAlt(_localctx, 6);
				{
				setState(631);
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
	public static class MatchExprContext extends ParserRuleContext {
		public TerminalNode MATCH() { return getToken(SolvikParser.MATCH, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode LBRACE() { return getToken(SolvikParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(SolvikParser.RBRACE, 0); }
		public List<MatchBranchContext> matchBranch() {
			return getRuleContexts(MatchBranchContext.class);
		}
		public MatchBranchContext matchBranch(int i) {
			return getRuleContext(MatchBranchContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SolvikParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SolvikParser.SEMI, i);
		}
		public MatchExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_matchExpr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitMatchExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MatchExprContext matchExpr() throws RecognitionException {
		MatchExprContext _localctx = new MatchExprContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_matchExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(634);
			match(MATCH);
			setState(635);
			expression();
			setState(636);
			match(LBRACE);
			setState(641);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Identifier || _la==SEMI) {
				{
				setState(639);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case Identifier:
					{
					setState(637);
					matchBranch();
					}
					break;
				case SEMI:
					{
					setState(638);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(643);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(644);
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
	public static class MatchBranchContext extends ParserRuleContext {
		public PatternContext pattern() {
			return getRuleContext(PatternContext.class,0);
		}
		public TerminalNode ARROW() { return getToken(SolvikParser.ARROW, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public MatchBranchContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_matchBranch; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitMatchBranch(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MatchBranchContext matchBranch() throws RecognitionException {
		MatchBranchContext _localctx = new MatchBranchContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_matchBranch);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(646);
			pattern();
			setState(647);
			match(ARROW);
			setState(648);
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
	public static class PatternContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode COLON() { return getToken(SolvikParser.COLON, 0); }
		public TypeRefContext typeRef() {
			return getRuleContext(TypeRefContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public PatternListContext patternList() {
			return getRuleContext(PatternListContext.class,0);
		}
		public PatternContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pattern; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitPattern(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PatternContext pattern() throws RecognitionException {
		PatternContext _localctx = new PatternContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_pattern);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(650);
			match(Identifier);
			setState(658);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(651);
				match(COLON);
				setState(652);
				typeRef();
				}
				break;
			case LPAREN:
				{
				setState(653);
				match(LPAREN);
				setState(655);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Identifier) {
					{
					setState(654);
					patternList();
					}
				}

				setState(657);
				match(RPAREN);
				}
				break;
			case ARROW:
			case RPAREN:
			case COMMA:
				break;
			default:
				break;
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
	public static class PatternListContext extends ParserRuleContext {
		public List<PatternContext> pattern() {
			return getRuleContexts(PatternContext.class);
		}
		public PatternContext pattern(int i) {
			return getRuleContext(PatternContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SolvikParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SolvikParser.COMMA, i);
		}
		public PatternListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_patternList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitPatternList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PatternListContext patternList() throws RecognitionException {
		PatternListContext _localctx = new PatternListContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_patternList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(660);
			pattern();
			setState(665);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(661);
				match(COMMA);
				setState(662);
				pattern();
				}
				}
				setState(667);
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
		enterRule(_localctx, 118, RULE_paren);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(668);
			match(LPAREN);
			setState(669);
			expression();
			setState(670);
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
		enterRule(_localctx, 120, RULE_thisExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(672);
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
		enterRule(_localctx, 122, RULE_superExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(674);
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
		enterRule(_localctx, 124, RULE_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(676);
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
		enterRule(_localctx, 126, RULE_suffix);
		try {
			setState(680);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DOT:
			case NULLABLE_DOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(678);
				memberSuffix();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(679);
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
		enterRule(_localctx, 128, RULE_memberSuffix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(682);
			_la = _input.LA(1);
			if ( !(_la==DOT || _la==NULLABLE_DOT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(683);
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
		enterRule(_localctx, 130, RULE_callSuffix);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(685);
			match(LPAREN);
			setState(687);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 12)) & ~0x3f) == 0 && ((1L << (_la - 12)) & 288269958837831683L) != 0)) {
				{
				setState(686);
				argumentList();
				}
			}

			setState(689);
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
		enterRule(_localctx, 132, RULE_argumentList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(691);
			expression();
			setState(696);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(692);
				match(COMMA);
				setState(693);
				expression();
				}
				}
				setState(698);
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
		enterRule(_localctx, 134, RULE_literal);
		try {
			setState(707);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(699);
				intLiteral();
				}
				break;
			case LONG_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(700);
				longLiteral();
				}
				break;
			case FLOATING_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(701);
				floatingLiteral();
				}
				break;
			case BOOL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(702);
				boolLiteral();
				}
				break;
			case CHAR_LITERAL:
				enterOuterAlt(_localctx, 5);
				{
				setState(703);
				charLiteral();
				}
				break;
			case STRING_LITERAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(704);
				stringLiteral();
				}
				break;
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(705);
				rawStringLiteral();
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 8);
				{
				setState(706);
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
		enterRule(_localctx, 136, RULE_intLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(709);
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
		enterRule(_localctx, 138, RULE_longLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(711);
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
		enterRule(_localctx, 140, RULE_floatingLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(713);
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
		enterRule(_localctx, 142, RULE_boolLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(715);
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
		enterRule(_localctx, 144, RULE_charLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(717);
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
		enterRule(_localctx, 146, RULE_stringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(719);
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
		enterRule(_localctx, 148, RULE_rawStringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(721);
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
		enterRule(_localctx, 150, RULE_nullLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(723);
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
		"\u0004\u0001F\u02d6\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
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
		"<\u0007<\u0002=\u0007=\u0002>\u0007>\u0002?\u0007?\u0002@\u0007@\u0002"+
		"A\u0007A\u0002B\u0007B\u0002C\u0007C\u0002D\u0007D\u0002E\u0007E\u0002"+
		"F\u0007F\u0002G\u0007G\u0002H\u0007H\u0002I\u0007I\u0002J\u0007J\u0002"+
		"K\u0007K\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0005"+
		"\u0000\u009e\b\u0000\n\u0000\f\u0000\u00a1\t\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u00a8\b\u0001\u0001\u0001"+
		"\u0001\u0001\u0003\u0001\u00ac\b\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0002\u0003\u0002\u00b4\b\u0002\u0001\u0002"+
		"\u0003\u0002\u00b7\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002"+
		"\u00bc\b\u0002\u0001\u0002\u0001\u0002\u0003\u0002\u00c0\b\u0002\u0001"+
		"\u0002\u0001\u0002\u0003\u0002\u00c4\b\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0005\u0002\u00c9\b\u0002\n\u0002\f\u0002\u00cc\t\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u00d3\b\u0003"+
		"\u0001\u0003\u0001\u0003\u0003\u0003\u00d7\b\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0005\u0003\u00dc\b\u0003\n\u0003\f\u0003\u00df\t\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004\u00e6"+
		"\b\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004\u00eb\b\u0004"+
		"\n\u0004\f\u0004\u00ee\t\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0003\u0005\u00f5\b\u0005\u0001\u0005\u0003\u0005\u00f8"+
		"\b\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0003\u0006\u00fe"+
		"\b\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u0103\b\u0007"+
		"\u0001\u0007\u0001\u0007\u0003\u0007\u0107\b\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0003\b\u0111"+
		"\b\b\u0001\b\u0001\b\u0003\b\u0115\b\b\u0001\b\u0001\b\u0001\b\u0001\b"+
		"\u0001\b\u0001\t\u0001\t\u0001\t\u0005\t\u011f\b\t\n\t\f\t\u0122\t\t\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0003\n\u0128\b\n\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b\u0131"+
		"\b\u000b\u0001\u000b\u0001\u000b\u0001\f\u0005\f\u0136\b\f\n\f\f\f\u0139"+
		"\t\f\u0001\f\u0001\f\u0001\f\u0003\f\u013e\b\f\u0001\f\u0001\f\u0003\f"+
		"\u0142\b\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0003"+
		"\u000e\u0151\b\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0003\u000f\u0158\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0005\u0010\u0160\b\u0010\n\u0010\f\u0010"+
		"\u0163\t\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0005\u0012\u016d\b\u0012\n\u0012"+
		"\f\u0012\u0170\t\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013"+
		"\u0003\u0013\u0176\b\u0013\u0001\u0013\u0003\u0013\u0179\b\u0013\u0001"+
		"\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0005\u0014\u017f\b\u0014\n"+
		"\u0014\f\u0014\u0182\t\u0014\u0001\u0014\u0001\u0014\u0001\u0015\u0001"+
		"\u0015\u0001\u0015\u0005\u0015\u0189\b\u0015\n\u0015\f\u0015\u018c\t\u0015"+
		"\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0003\u0016"+
		"\u0199\b\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0003\u0017"+
		"\u019f\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0018"+
		"\u0001\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0003\u0019\u01ad\b\u0019\u0001\u001a\u0001\u001a\u0001\u001a"+
		"\u0001\u001a\u0003\u001a\u01b3\b\u001a\u0001\u001b\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c"+
		"\u0003\u001c\u01be\b\u001c\u0001\u001c\u0001\u001c\u0003\u001c\u01c2\b"+
		"\u001c\u0001\u001c\u0001\u001c\u0003\u001c\u01c6\b\u001c\u0001\u001c\u0001"+
		"\u001c\u0001\u001c\u0001\u001d\u0001\u001d\u0003\u001d\u01cd\b\u001d\u0001"+
		"\u001e\u0001\u001e\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0001 \u0001 \u0001 \u0005 \u01db\b \n \f \u01de\t \u0001 \u0001"+
		" \u0001!\u0001!\u0001!\u0001!\u0005!\u01e6\b!\n!\f!\u01e9\t!\u0001!\u0001"+
		"!\u0001!\u0005!\u01ee\b!\n!\f!\u01f1\t!\u0001\"\u0001\"\u0001\"\u0001"+
		"\"\u0005\"\u01f7\b\"\n\"\f\"\u01fa\t\"\u0001#\u0001#\u0003#\u01fe\b#\u0001"+
		"$\u0001$\u0001$\u0003$\u0203\b$\u0001%\u0001%\u0001%\u0001%\u0003%\u0209"+
		"\b%\u0001%\u0001%\u0001%\u0001&\u0001&\u0001&\u0003&\u0211\b&\u0001\'"+
		"\u0001\'\u0001\'\u0001(\u0001(\u0001(\u0001)\u0001)\u0003)\u021b\b)\u0001"+
		")\u0001)\u0001*\u0001*\u0001*\u0003*\u0222\b*\u0001*\u0001*\u0001+\u0001"+
		"+\u0001,\u0001,\u0001,\u0005,\u022b\b,\n,\f,\u022e\t,\u0001-\u0001-\u0001"+
		"-\u0005-\u0233\b-\n-\f-\u0236\t-\u0001.\u0001.\u0001.\u0005.\u023b\b."+
		"\n.\f.\u023e\t.\u0001/\u0001/\u0001/\u0005/\u0243\b/\n/\f/\u0246\t/\u0001"+
		"0\u00010\u00050\u024a\b0\n0\f0\u024d\t0\u00011\u00011\u00011\u00011\u0001"+
		"1\u00011\u00031\u0255\b1\u00012\u00012\u00012\u00052\u025a\b2\n2\f2\u025d"+
		"\t2\u00013\u00013\u00013\u00053\u0262\b3\n3\f3\u0265\t3\u00014\u00014"+
		"\u00014\u00034\u026a\b4\u00015\u00015\u00055\u026e\b5\n5\f5\u0271\t5\u0001"+
		"6\u00016\u00016\u00016\u00016\u00016\u00036\u0279\b6\u00017\u00017\u0001"+
		"7\u00017\u00017\u00057\u0280\b7\n7\f7\u0283\t7\u00017\u00017\u00018\u0001"+
		"8\u00018\u00018\u00019\u00019\u00019\u00019\u00019\u00039\u0290\b9\u0001"+
		"9\u00039\u0293\b9\u0001:\u0001:\u0001:\u0005:\u0298\b:\n:\f:\u029b\t:"+
		"\u0001;\u0001;\u0001;\u0001;\u0001<\u0001<\u0001=\u0001=\u0001>\u0001"+
		">\u0001?\u0001?\u0003?\u02a9\b?\u0001@\u0001@\u0001@\u0001A\u0001A\u0003"+
		"A\u02b0\bA\u0001A\u0001A\u0001B\u0001B\u0001B\u0005B\u02b7\bB\nB\fB\u02ba"+
		"\tB\u0001C\u0001C\u0001C\u0001C\u0001C\u0001C\u0001C\u0001C\u0003C\u02c4"+
		"\bC\u0001D\u0001D\u0001E\u0001E\u0001F\u0001F\u0001G\u0001G\u0001H\u0001"+
		"H\u0001I\u0001I\u0001J\u0001J\u0001K\u0001K\u0001K\u0000\u0000L\u0000"+
		"\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c"+
		"\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtvxz|~\u0080\u0082\u0084"+
		"\u0086\u0088\u008a\u008c\u008e\u0090\u0092\u0094\u0096\u0000\b\u0002\u0000"+
		"\b\b\n\n\u0001\u0000\u000e\u000f\u0001\u0000:;\u0001\u0000<?\u0001\u0000"+
		"56\u0001\u000078\u0002\u00006699\u0001\u0000/0\u02f3\u0000\u009f\u0001"+
		"\u0000\u0000\u0000\u0002\u00a4\u0001\u0000\u0000\u0000\u0004\u00b3\u0001"+
		"\u0000\u0000\u0000\u0006\u00cf\u0001\u0000\u0000\u0000\b\u00e2\u0001\u0000"+
		"\u0000\u0000\n\u00f1\u0001\u0000\u0000\u0000\f\u00fd\u0001\u0000\u0000"+
		"\u0000\u000e\u00ff\u0001\u0000\u0000\u0000\u0010\u010d\u0001\u0000\u0000"+
		"\u0000\u0012\u011b\u0001\u0000\u0000\u0000\u0014\u0127\u0001\u0000\u0000"+
		"\u0000\u0016\u0129\u0001\u0000\u0000\u0000\u0018\u0137\u0001\u0000\u0000"+
		"\u0000\u001a\u0148\u0001\u0000\u0000\u0000\u001c\u014a\u0001\u0000\u0000"+
		"\u0000\u001e\u0154\u0001\u0000\u0000\u0000 \u015c\u0001\u0000\u0000\u0000"+
		"\"\u0164\u0001\u0000\u0000\u0000$\u0168\u0001\u0000\u0000\u0000&\u0173"+
		"\u0001\u0000\u0000\u0000(\u017a\u0001\u0000\u0000\u0000*\u0185\u0001\u0000"+
		"\u0000\u0000,\u0198\u0001\u0000\u0000\u0000.\u019a\u0001\u0000\u0000\u0000"+
		"0\u01a4\u0001\u0000\u0000\u00002\u01a6\u0001\u0000\u0000\u00004\u01b2"+
		"\u0001\u0000\u0000\u00006\u01b4\u0001\u0000\u0000\u00008\u01ba\u0001\u0000"+
		"\u0000\u0000:\u01cc\u0001\u0000\u0000\u0000<\u01ce\u0001\u0000\u0000\u0000"+
		">\u01d0\u0001\u0000\u0000\u0000@\u01d2\u0001\u0000\u0000\u0000B\u01e1"+
		"\u0001\u0000\u0000\u0000D\u01f2\u0001\u0000\u0000\u0000F\u01fd\u0001\u0000"+
		"\u0000\u0000H\u01ff\u0001\u0000\u0000\u0000J\u0204\u0001\u0000\u0000\u0000"+
		"L\u020d\u0001\u0000\u0000\u0000N\u0212\u0001\u0000\u0000\u0000P\u0215"+
		"\u0001\u0000\u0000\u0000R\u0218\u0001\u0000\u0000\u0000T\u021e\u0001\u0000"+
		"\u0000\u0000V\u0225\u0001\u0000\u0000\u0000X\u0227\u0001\u0000\u0000\u0000"+
		"Z\u022f\u0001\u0000\u0000\u0000\\\u0237\u0001\u0000\u0000\u0000^\u023f"+
		"\u0001\u0000\u0000\u0000`\u0247\u0001\u0000\u0000\u0000b\u0254\u0001\u0000"+
		"\u0000\u0000d\u0256\u0001\u0000\u0000\u0000f\u025e\u0001\u0000\u0000\u0000"+
		"h\u0269\u0001\u0000\u0000\u0000j\u026b\u0001\u0000\u0000\u0000l\u0278"+
		"\u0001\u0000\u0000\u0000n\u027a\u0001\u0000\u0000\u0000p\u0286\u0001\u0000"+
		"\u0000\u0000r\u028a\u0001\u0000\u0000\u0000t\u0294\u0001\u0000\u0000\u0000"+
		"v\u029c\u0001\u0000\u0000\u0000x\u02a0\u0001\u0000\u0000\u0000z\u02a2"+
		"\u0001\u0000\u0000\u0000|\u02a4\u0001\u0000\u0000\u0000~\u02a8\u0001\u0000"+
		"\u0000\u0000\u0080\u02aa\u0001\u0000\u0000\u0000\u0082\u02ad\u0001\u0000"+
		"\u0000\u0000\u0084\u02b3\u0001\u0000\u0000\u0000\u0086\u02c3\u0001\u0000"+
		"\u0000\u0000\u0088\u02c5\u0001\u0000\u0000\u0000\u008a\u02c7\u0001\u0000"+
		"\u0000\u0000\u008c\u02c9\u0001\u0000\u0000\u0000\u008e\u02cb\u0001\u0000"+
		"\u0000\u0000\u0090\u02cd\u0001\u0000\u0000\u0000\u0092\u02cf\u0001\u0000"+
		"\u0000\u0000\u0094\u02d1\u0001\u0000\u0000\u0000\u0096\u02d3\u0001\u0000"+
		"\u0000\u0000\u0098\u009e\u0003\u0002\u0001\u0000\u0099\u009e\u0003\u0004"+
		"\u0002\u0000\u009a\u009e\u0003\u0006\u0003\u0000\u009b\u009e\u0003\b\u0004"+
		"\u0000\u009c\u009e\u0005+\u0000\u0000\u009d\u0098\u0001\u0000\u0000\u0000"+
		"\u009d\u0099\u0001\u0000\u0000\u0000\u009d\u009a\u0001\u0000\u0000\u0000"+
		"\u009d\u009b\u0001\u0000\u0000\u0000\u009d\u009c\u0001\u0000\u0000\u0000"+
		"\u009e\u00a1\u0001\u0000\u0000\u0000\u009f\u009d\u0001\u0000\u0000\u0000"+
		"\u009f\u00a0\u0001\u0000\u0000\u0000\u00a0\u00a2\u0001\u0000\u0000\u0000"+
		"\u00a1\u009f\u0001\u0000\u0000\u0000\u00a2\u00a3\u0005\u0000\u0000\u0001"+
		"\u00a3\u0001\u0001\u0000\u0000\u0000\u00a4\u00a5\u0005\u0001\u0000\u0000"+
		"\u00a5\u00a7\u0005!\u0000\u0000\u00a6\u00a8\u0003$\u0012\u0000\u00a7\u00a6"+
		"\u0001\u0000\u0000\u0000\u00a7\u00a8\u0001\u0000\u0000\u0000\u00a8\u00a9"+
		"\u0001\u0000\u0000\u0000\u00a9\u00ab\u0005\'\u0000\u0000\u00aa\u00ac\u0003"+
		" \u0010\u0000\u00ab\u00aa\u0001\u0000\u0000\u0000\u00ab\u00ac\u0001\u0000"+
		"\u0000\u0000\u00ac\u00ad\u0001\u0000\u0000\u0000\u00ad\u00ae\u0005(\u0000"+
		"\u0000\u00ae\u00af\u0005-\u0000\u0000\u00af\u00b0\u0003&\u0013\u0000\u00b0"+
		"\u00b1\u0003*\u0015\u0000\u00b1\u0003\u0001\u0000\u0000\u0000\u00b2\u00b4"+
		"\u0005\u0005\u0000\u0000\u00b3\u00b2\u0001\u0000\u0000\u0000\u00b3\u00b4"+
		"\u0001\u0000\u0000\u0000\u00b4\u00b6\u0001\u0000\u0000\u0000\u00b5\u00b7"+
		"\u0005\b\u0000\u0000\u00b6\u00b5\u0001\u0000\u0000\u0000\u00b6\u00b7\u0001"+
		"\u0000\u0000\u0000\u00b7\u00b8\u0001\u0000\u0000\u0000\u00b8\u00b9\u0005"+
		"\u0002\u0000\u0000\u00b9\u00bb\u0005!\u0000\u0000\u00ba\u00bc\u0003$\u0012"+
		"\u0000\u00bb\u00ba\u0001\u0000\u0000\u0000\u00bb\u00bc\u0001\u0000\u0000"+
		"\u0000\u00bc\u00bf\u0001\u0000\u0000\u0000\u00bd\u00be\u0005\t\u0000\u0000"+
		"\u00be\u00c0\u0003&\u0013\u0000\u00bf\u00bd\u0001\u0000\u0000\u0000\u00bf"+
		"\u00c0\u0001\u0000\u0000\u0000\u00c0\u00c3\u0001\u0000\u0000\u0000\u00c1"+
		"\u00c2\u0005\u0007\u0000\u0000\u00c2\u00c4\u0003\u0012\t\u0000\u00c3\u00c1"+
		"\u0001\u0000\u0000\u0000\u00c3\u00c4\u0001\u0000\u0000\u0000\u00c4\u00c5"+
		"\u0001\u0000\u0000\u0000\u00c5\u00ca\u0005)\u0000\u0000\u00c6\u00c9\u0003"+
		"\u0014\n\u0000\u00c7\u00c9\u0005+\u0000\u0000\u00c8\u00c6\u0001\u0000"+
		"\u0000\u0000\u00c8\u00c7\u0001\u0000\u0000\u0000\u00c9\u00cc\u0001\u0000"+
		"\u0000\u0000\u00ca\u00c8\u0001\u0000\u0000\u0000\u00ca\u00cb\u0001\u0000"+
		"\u0000\u0000\u00cb\u00cd\u0001\u0000\u0000\u0000\u00cc\u00ca\u0001\u0000"+
		"\u0000\u0000\u00cd\u00ce\u0005*\u0000\u0000\u00ce\u0005\u0001\u0000\u0000"+
		"\u0000\u00cf\u00d0\u0005\u0003\u0000\u0000\u00d0\u00d2\u0005!\u0000\u0000"+
		"\u00d1\u00d3\u0003$\u0012\u0000\u00d2\u00d1\u0001\u0000\u0000\u0000\u00d2"+
		"\u00d3\u0001\u0000\u0000\u0000\u00d3\u00d6\u0001\u0000\u0000\u0000\u00d4"+
		"\u00d5\u0005\t\u0000\u0000\u00d5\u00d7\u0003\u0012\t\u0000\u00d6\u00d4"+
		"\u0001\u0000\u0000\u0000\u00d6\u00d7\u0001\u0000\u0000\u0000\u00d7\u00d8"+
		"\u0001\u0000\u0000\u0000\u00d8\u00dd\u0005)\u0000\u0000\u00d9\u00dc\u0003"+
		"\f\u0006\u0000\u00da\u00dc\u0005+\u0000\u0000\u00db\u00d9\u0001\u0000"+
		"\u0000\u0000\u00db\u00da\u0001\u0000\u0000\u0000\u00dc\u00df\u0001\u0000"+
		"\u0000\u0000\u00dd\u00db\u0001\u0000\u0000\u0000\u00dd\u00de\u0001\u0000"+
		"\u0000\u0000\u00de\u00e0\u0001\u0000\u0000\u0000\u00df\u00dd\u0001\u0000"+
		"\u0000\u0000\u00e0\u00e1\u0005*\u0000\u0000\u00e1\u0007\u0001\u0000\u0000"+
		"\u0000\u00e2\u00e3\u0005\u0004\u0000\u0000\u00e3\u00e5\u0005!\u0000\u0000"+
		"\u00e4\u00e6\u0003$\u0012\u0000\u00e5\u00e4\u0001\u0000\u0000\u0000\u00e5"+
		"\u00e6\u0001\u0000\u0000\u0000\u00e6\u00e7\u0001\u0000\u0000\u0000\u00e7"+
		"\u00ec\u0005)\u0000\u0000\u00e8\u00eb\u0003\n\u0005\u0000\u00e9\u00eb"+
		"\u0005+\u0000\u0000\u00ea\u00e8\u0001\u0000\u0000\u0000\u00ea\u00e9\u0001"+
		"\u0000\u0000\u0000\u00eb\u00ee\u0001\u0000\u0000\u0000\u00ec\u00ea\u0001"+
		"\u0000\u0000\u0000\u00ec\u00ed\u0001\u0000\u0000\u0000\u00ed\u00ef\u0001"+
		"\u0000\u0000\u0000\u00ee\u00ec\u0001\u0000\u0000\u0000\u00ef\u00f0\u0005"+
		"*\u0000\u0000\u00f0\t\u0001\u0000\u0000\u0000\u00f1\u00f7\u0005!\u0000"+
		"\u0000\u00f2\u00f4\u0005\'\u0000\u0000\u00f3\u00f5\u0003\u0012\t\u0000"+
		"\u00f4\u00f3\u0001\u0000\u0000\u0000\u00f4\u00f5\u0001\u0000\u0000\u0000"+
		"\u00f5\u00f6\u0001\u0000\u0000\u0000\u00f6\u00f8\u0005(\u0000\u0000\u00f7"+
		"\u00f2\u0001\u0000\u0000\u0000\u00f7\u00f8\u0001\u0000\u0000\u0000\u00f8"+
		"\u00f9\u0001\u0000\u0000\u0000\u00f9\u00fa\u0005+\u0000\u0000\u00fa\u000b"+
		"\u0001\u0000\u0000\u0000\u00fb\u00fe\u0003\u000e\u0007\u0000\u00fc\u00fe"+
		"\u0003\u0010\b\u0000\u00fd\u00fb\u0001\u0000\u0000\u0000\u00fd\u00fc\u0001"+
		"\u0000\u0000\u0000\u00fe\r\u0001\u0000\u0000\u0000\u00ff\u0100\u0005\u0001"+
		"\u0000\u0000\u0100\u0102\u0005!\u0000\u0000\u0101\u0103\u0003$\u0012\u0000"+
		"\u0102\u0101\u0001\u0000\u0000\u0000\u0102\u0103\u0001\u0000\u0000\u0000"+
		"\u0103\u0104\u0001\u0000\u0000\u0000\u0104\u0106\u0005\'\u0000\u0000\u0105"+
		"\u0107\u0003 \u0010\u0000\u0106\u0105\u0001\u0000\u0000\u0000\u0106\u0107"+
		"\u0001\u0000\u0000\u0000\u0107\u0108\u0001\u0000\u0000\u0000\u0108\u0109"+
		"\u0005(\u0000\u0000\u0109\u010a\u0005-\u0000\u0000\u010a\u010b\u0003&"+
		"\u0013\u0000\u010b\u010c\u0005+\u0000\u0000\u010c\u000f\u0001\u0000\u0000"+
		"\u0000\u010d\u010e\u0005\u0001\u0000\u0000\u010e\u0110\u0005!\u0000\u0000"+
		"\u010f\u0111\u0003$\u0012\u0000\u0110\u010f\u0001\u0000\u0000\u0000\u0110"+
		"\u0111\u0001\u0000\u0000\u0000\u0111\u0112\u0001\u0000\u0000\u0000\u0112"+
		"\u0114\u0005\'\u0000\u0000\u0113\u0115\u0003 \u0010\u0000\u0114\u0113"+
		"\u0001\u0000\u0000\u0000\u0114\u0115\u0001\u0000\u0000\u0000\u0115\u0116"+
		"\u0001\u0000\u0000\u0000\u0116\u0117\u0005(\u0000\u0000\u0117\u0118\u0005"+
		"-\u0000\u0000\u0118\u0119\u0003&\u0013\u0000\u0119\u011a\u0003*\u0015"+
		"\u0000\u011a\u0011\u0001\u0000\u0000\u0000\u011b\u0120\u0003&\u0013\u0000"+
		"\u011c\u011d\u0005.\u0000\u0000\u011d\u011f\u0003&\u0013\u0000\u011e\u011c"+
		"\u0001\u0000\u0000\u0000\u011f\u0122\u0001\u0000\u0000\u0000\u0120\u011e"+
		"\u0001\u0000\u0000\u0000\u0120\u0121\u0001\u0000\u0000\u0000\u0121\u0013"+
		"\u0001\u0000\u0000\u0000\u0122\u0120\u0001\u0000\u0000\u0000\u0123\u0128"+
		"\u0003\u001c\u000e\u0000\u0124\u0128\u0003\u0016\u000b\u0000\u0125\u0128"+
		"\u0003\u001e\u000f\u0000\u0126\u0128\u0003\u0018\f\u0000\u0127\u0123\u0001"+
		"\u0000\u0000\u0000\u0127\u0124\u0001\u0000\u0000\u0000\u0127\u0125\u0001"+
		"\u0000\u0000\u0000\u0127\u0126\u0001\u0000\u0000\u0000\u0128\u0015\u0001"+
		"\u0000\u0000\u0000\u0129\u012a\u0005\u0006\u0000\u0000\u012a\u012b\u0005"+
		"\u000e\u0000\u0000\u012b\u012c\u0005!\u0000\u0000\u012c\u012d\u0005-\u0000"+
		"\u0000\u012d\u0130\u0003&\u0013\u0000\u012e\u012f\u0005,\u0000\u0000\u012f"+
		"\u0131\u0003V+\u0000\u0130\u012e\u0001\u0000\u0000\u0000\u0130\u0131\u0001"+
		"\u0000\u0000\u0000\u0131\u0132\u0001\u0000\u0000\u0000\u0132\u0133\u0005"+
		"+\u0000\u0000\u0133\u0017\u0001\u0000\u0000\u0000\u0134\u0136\u0003\u001a"+
		"\r\u0000\u0135\u0134\u0001\u0000\u0000\u0000\u0136\u0139\u0001\u0000\u0000"+
		"\u0000\u0137\u0135\u0001\u0000\u0000\u0000\u0137\u0138\u0001\u0000\u0000"+
		"\u0000\u0138\u013a\u0001\u0000\u0000\u0000\u0139\u0137\u0001\u0000\u0000"+
		"\u0000\u013a\u013b\u0005\u0001\u0000\u0000\u013b\u013d\u0005!\u0000\u0000"+
		"\u013c\u013e\u0003$\u0012\u0000\u013d\u013c\u0001\u0000\u0000\u0000\u013d"+
		"\u013e\u0001\u0000\u0000\u0000\u013e\u013f\u0001\u0000\u0000\u0000\u013f"+
		"\u0141\u0005\'\u0000\u0000\u0140\u0142\u0003 \u0010\u0000\u0141\u0140"+
		"\u0001\u0000\u0000\u0000\u0141\u0142\u0001\u0000\u0000\u0000\u0142\u0143"+
		"\u0001\u0000\u0000\u0000\u0143\u0144\u0005(\u0000\u0000\u0144\u0145\u0005"+
		"-\u0000\u0000\u0145\u0146\u0003&\u0013\u0000\u0146\u0147\u0003*\u0015"+
		"\u0000\u0147\u0019\u0001\u0000\u0000\u0000\u0148\u0149\u0007\u0000\u0000"+
		"\u0000\u0149\u001b\u0001\u0000\u0000\u0000\u014a\u014b\u00030\u0018\u0000"+
		"\u014b\u014c\u0005!\u0000\u0000\u014c\u014d\u0005-\u0000\u0000\u014d\u0150"+
		"\u0003&\u0013\u0000\u014e\u014f\u0005,\u0000\u0000\u014f\u0151\u0003V"+
		"+\u0000\u0150\u014e\u0001\u0000\u0000\u0000\u0150\u0151\u0001\u0000\u0000"+
		"\u0000\u0151\u0152\u0001\u0000\u0000\u0000\u0152\u0153\u0005+\u0000\u0000"+
		"\u0153\u001d\u0001\u0000\u0000\u0000\u0154\u0155\u0005\u000b\u0000\u0000"+
		"\u0155\u0157\u0005\'\u0000\u0000\u0156\u0158\u0003 \u0010\u0000\u0157"+
		"\u0156\u0001\u0000\u0000\u0000\u0157\u0158\u0001\u0000\u0000\u0000\u0158"+
		"\u0159\u0001\u0000\u0000\u0000\u0159\u015a\u0005(\u0000\u0000\u015a\u015b"+
		"\u0003*\u0015\u0000\u015b\u001f\u0001\u0000\u0000\u0000\u015c\u0161\u0003"+
		"\"\u0011\u0000\u015d\u015e\u0005.\u0000\u0000\u015e\u0160\u0003\"\u0011"+
		"\u0000\u015f\u015d\u0001\u0000\u0000\u0000\u0160\u0163\u0001\u0000\u0000"+
		"\u0000\u0161\u015f\u0001\u0000\u0000\u0000\u0161\u0162\u0001\u0000\u0000"+
		"\u0000\u0162!\u0001\u0000\u0000\u0000\u0163\u0161\u0001\u0000\u0000\u0000"+
		"\u0164\u0165\u0005!\u0000\u0000\u0165\u0166\u0005-\u0000\u0000\u0166\u0167"+
		"\u0003&\u0013\u0000\u0167#\u0001\u0000\u0000\u0000\u0168\u0169\u0005<"+
		"\u0000\u0000\u0169\u016e\u0005!\u0000\u0000\u016a\u016b\u0005.\u0000\u0000"+
		"\u016b\u016d\u0005!\u0000\u0000\u016c\u016a\u0001\u0000\u0000\u0000\u016d"+
		"\u0170\u0001\u0000\u0000\u0000\u016e\u016c\u0001\u0000\u0000\u0000\u016e"+
		"\u016f\u0001\u0000\u0000\u0000\u016f\u0171\u0001\u0000\u0000\u0000\u0170"+
		"\u016e\u0001\u0000\u0000\u0000\u0171\u0172\u0005>\u0000\u0000\u0172%\u0001"+
		"\u0000\u0000\u0000\u0173\u0175\u0005!\u0000\u0000\u0174\u0176\u0003(\u0014"+
		"\u0000\u0175\u0174\u0001\u0000\u0000\u0000\u0175\u0176\u0001\u0000\u0000"+
		"\u0000\u0176\u0178\u0001\u0000\u0000\u0000\u0177\u0179\u00052\u0000\u0000"+
		"\u0178\u0177\u0001\u0000\u0000\u0000\u0178\u0179\u0001\u0000\u0000\u0000"+
		"\u0179\'\u0001\u0000\u0000\u0000\u017a\u017b\u0005<\u0000\u0000\u017b"+
		"\u0180\u0003&\u0013\u0000\u017c\u017d\u0005.\u0000\u0000\u017d\u017f\u0003"+
		"&\u0013\u0000\u017e\u017c\u0001\u0000\u0000\u0000\u017f\u0182\u0001\u0000"+
		"\u0000\u0000\u0180\u017e\u0001\u0000\u0000\u0000\u0180\u0181\u0001\u0000"+
		"\u0000\u0000\u0181\u0183\u0001\u0000\u0000\u0000\u0182\u0180\u0001\u0000"+
		"\u0000\u0000\u0183\u0184\u0005>\u0000\u0000\u0184)\u0001\u0000\u0000\u0000"+
		"\u0185\u018a\u0005)\u0000\u0000\u0186\u0189\u0003,\u0016\u0000\u0187\u0189"+
		"\u0005+\u0000\u0000\u0188\u0186\u0001\u0000\u0000\u0000\u0188\u0187\u0001"+
		"\u0000\u0000\u0000\u0189\u018c\u0001\u0000\u0000\u0000\u018a\u0188\u0001"+
		"\u0000\u0000\u0000\u018a\u018b\u0001\u0000\u0000\u0000\u018b\u018d\u0001"+
		"\u0000\u0000\u0000\u018c\u018a\u0001\u0000\u0000\u0000\u018d\u018e\u0005"+
		"*\u0000\u0000\u018e+\u0001\u0000\u0000\u0000\u018f\u0199\u0003.\u0017"+
		"\u0000\u0190\u0199\u00032\u0019\u0000\u0191\u0199\u00036\u001b\u0000\u0192"+
		"\u0199\u00038\u001c\u0000\u0193\u0199\u0003@ \u0000\u0194\u0199\u0003"+
		"N\'\u0000\u0195\u0199\u0003P(\u0000\u0196\u0199\u0003R)\u0000\u0197\u0199"+
		"\u0003T*\u0000\u0198\u018f\u0001\u0000\u0000\u0000\u0198\u0190\u0001\u0000"+
		"\u0000\u0000\u0198\u0191\u0001\u0000\u0000\u0000\u0198\u0192\u0001\u0000"+
		"\u0000\u0000\u0198\u0193\u0001\u0000\u0000\u0000\u0198\u0194\u0001\u0000"+
		"\u0000\u0000\u0198\u0195\u0001\u0000\u0000\u0000\u0198\u0196\u0001\u0000"+
		"\u0000\u0000\u0198\u0197\u0001\u0000\u0000\u0000\u0199-\u0001\u0000\u0000"+
		"\u0000\u019a\u019b\u00030\u0018\u0000\u019b\u019e\u0005!\u0000\u0000\u019c"+
		"\u019d\u0005-\u0000\u0000\u019d\u019f\u0003&\u0013\u0000\u019e\u019c\u0001"+
		"\u0000\u0000\u0000\u019e\u019f\u0001\u0000\u0000\u0000\u019f\u01a0\u0001"+
		"\u0000\u0000\u0000\u01a0\u01a1\u0005,\u0000\u0000\u01a1\u01a2\u0003V+"+
		"\u0000\u01a2\u01a3\u0005+\u0000\u0000\u01a3/\u0001\u0000\u0000\u0000\u01a4"+
		"\u01a5\u0007\u0001\u0000\u0000\u01a51\u0001\u0000\u0000\u0000\u01a6\u01a7"+
		"\u0005\u0010\u0000\u0000\u01a7\u01a8\u0005\'\u0000\u0000\u01a8\u01a9\u0003"+
		"V+\u0000\u01a9\u01aa\u0005(\u0000\u0000\u01aa\u01ac\u0003*\u0015\u0000"+
		"\u01ab\u01ad\u00034\u001a\u0000\u01ac\u01ab\u0001\u0000\u0000\u0000\u01ac"+
		"\u01ad\u0001\u0000\u0000\u0000\u01ad3\u0001\u0000\u0000\u0000\u01ae\u01af"+
		"\u0005\u0011\u0000\u0000\u01af\u01b3\u00032\u0019\u0000\u01b0\u01b1\u0005"+
		"\u0011\u0000\u0000\u01b1\u01b3\u0003*\u0015\u0000\u01b2\u01ae\u0001\u0000"+
		"\u0000\u0000\u01b2\u01b0\u0001\u0000\u0000\u0000\u01b35\u0001\u0000\u0000"+
		"\u0000\u01b4\u01b5\u0005\u0012\u0000\u0000\u01b5\u01b6\u0005\'\u0000\u0000"+
		"\u01b6\u01b7\u0003V+\u0000\u01b7\u01b8\u0005(\u0000\u0000\u01b8\u01b9"+
		"\u0003*\u0015\u0000\u01b97\u0001\u0000\u0000\u0000\u01ba\u01bb\u0005\u0013"+
		"\u0000\u0000\u01bb\u01bd\u0005\'\u0000\u0000\u01bc\u01be\u0003:\u001d"+
		"\u0000\u01bd\u01bc\u0001\u0000\u0000\u0000\u01bd\u01be\u0001\u0000\u0000"+
		"\u0000\u01be\u01bf\u0001\u0000\u0000\u0000\u01bf\u01c1\u0005+\u0000\u0000"+
		"\u01c0\u01c2\u0003<\u001e\u0000\u01c1\u01c0\u0001\u0000\u0000\u0000\u01c1"+
		"\u01c2\u0001\u0000\u0000\u0000\u01c2\u01c3\u0001\u0000\u0000\u0000\u01c3"+
		"\u01c5\u0005+\u0000\u0000\u01c4\u01c6\u0003>\u001f\u0000\u01c5\u01c4\u0001"+
		"\u0000\u0000\u0000\u01c5\u01c6\u0001\u0000\u0000\u0000\u01c6\u01c7\u0001"+
		"\u0000\u0000\u0000\u01c7\u01c8\u0005(\u0000\u0000\u01c8\u01c9\u0003*\u0015"+
		"\u0000\u01c99\u0001\u0000\u0000\u0000\u01ca\u01cd\u0003J%\u0000\u01cb"+
		"\u01cd\u0003L&\u0000\u01cc\u01ca\u0001\u0000\u0000\u0000\u01cc\u01cb\u0001"+
		"\u0000\u0000\u0000\u01cd;\u0001\u0000\u0000\u0000\u01ce\u01cf\u0003V+"+
		"\u0000\u01cf=\u0001\u0000\u0000\u0000\u01d0\u01d1\u0003L&\u0000\u01d1"+
		"?\u0001\u0000\u0000\u0000\u01d2\u01d3\u0005\u0019\u0000\u0000\u01d3\u01d4"+
		"\u0005\'\u0000\u0000\u01d4\u01d5\u0003V+\u0000\u01d5\u01d6\u0005(\u0000"+
		"\u0000\u01d6\u01dc\u0005)\u0000\u0000\u01d7\u01db\u0003B!\u0000\u01d8"+
		"\u01db\u0003D\"\u0000\u01d9\u01db\u0005+\u0000\u0000\u01da\u01d7\u0001"+
		"\u0000\u0000\u0000\u01da\u01d8\u0001\u0000\u0000\u0000\u01da\u01d9\u0001"+
		"\u0000\u0000\u0000\u01db\u01de\u0001\u0000\u0000\u0000\u01dc\u01da\u0001"+
		"\u0000\u0000\u0000\u01dc\u01dd\u0001\u0000\u0000\u0000\u01dd\u01df\u0001"+
		"\u0000\u0000\u0000\u01de\u01dc\u0001\u0000\u0000\u0000\u01df\u01e0\u0005"+
		"*\u0000\u0000\u01e0A\u0001\u0000\u0000\u0000\u01e1\u01e2\u0005\u001a\u0000"+
		"\u0000\u01e2\u01e7\u0003F#\u0000\u01e3\u01e4\u0005.\u0000\u0000\u01e4"+
		"\u01e6\u0003F#\u0000\u01e5\u01e3\u0001\u0000\u0000\u0000\u01e6\u01e9\u0001"+
		"\u0000\u0000\u0000\u01e7\u01e5\u0001\u0000\u0000\u0000\u01e7\u01e8\u0001"+
		"\u0000\u0000\u0000\u01e8\u01ea\u0001\u0000\u0000\u0000\u01e9\u01e7\u0001"+
		"\u0000\u0000\u0000\u01ea\u01ef\u0005-\u0000\u0000\u01eb\u01ee\u0003,\u0016"+
		"\u0000\u01ec\u01ee\u0005+\u0000\u0000\u01ed\u01eb\u0001\u0000\u0000\u0000"+
		"\u01ed\u01ec\u0001\u0000\u0000\u0000\u01ee\u01f1\u0001\u0000\u0000\u0000"+
		"\u01ef\u01ed\u0001\u0000\u0000\u0000\u01ef\u01f0\u0001\u0000\u0000\u0000"+
		"\u01f0C\u0001\u0000\u0000\u0000\u01f1\u01ef\u0001\u0000\u0000\u0000\u01f2"+
		"\u01f3\u0005\u001b\u0000\u0000\u01f3\u01f8\u0005-\u0000\u0000\u01f4\u01f7"+
		"\u0003,\u0016\u0000\u01f5\u01f7\u0005+\u0000\u0000\u01f6\u01f4\u0001\u0000"+
		"\u0000\u0000\u01f6\u01f5\u0001\u0000\u0000\u0000\u01f7\u01fa\u0001\u0000"+
		"\u0000\u0000\u01f8\u01f6\u0001\u0000\u0000\u0000\u01f8\u01f9\u0001\u0000"+
		"\u0000\u0000\u01f9E\u0001\u0000\u0000\u0000\u01fa\u01f8\u0001\u0000\u0000"+
		"\u0000\u01fb\u01fe\u0003H$\u0000\u01fc\u01fe\u0003V+\u0000\u01fd\u01fb"+
		"\u0001\u0000\u0000\u0000\u01fd\u01fc\u0001\u0000\u0000\u0000\u01feG\u0001"+
		"\u0000\u0000\u0000\u01ff\u0202\u0005\u001c\u0000\u0000\u0200\u0203\u0003"+
		"\u0092I\u0000\u0201\u0203\u0003\u0094J\u0000\u0202\u0200\u0001\u0000\u0000"+
		"\u0000\u0202\u0201\u0001\u0000\u0000\u0000\u0203I\u0001\u0000\u0000\u0000"+
		"\u0204\u0205\u00030\u0018\u0000\u0205\u0208\u0005!\u0000\u0000\u0206\u0207"+
		"\u0005-\u0000\u0000\u0207\u0209\u0003&\u0013\u0000\u0208\u0206\u0001\u0000"+
		"\u0000\u0000\u0208\u0209\u0001\u0000\u0000\u0000\u0209\u020a\u0001\u0000"+
		"\u0000\u0000\u020a\u020b\u0005,\u0000\u0000\u020b\u020c\u0003V+\u0000"+
		"\u020cK\u0001\u0000\u0000\u0000\u020d\u0210\u0003V+\u0000\u020e\u020f"+
		"\u0005,\u0000\u0000\u020f\u0211\u0003V+\u0000\u0210\u020e\u0001\u0000"+
		"\u0000\u0000\u0210\u0211\u0001\u0000\u0000\u0000\u0211M\u0001\u0000\u0000"+
		"\u0000\u0212\u0213\u0005\u0014\u0000\u0000\u0213\u0214\u0005+\u0000\u0000"+
		"\u0214O\u0001\u0000\u0000\u0000\u0215\u0216\u0005\u0015\u0000\u0000\u0216"+
		"\u0217\u0005+\u0000\u0000\u0217Q\u0001\u0000\u0000\u0000\u0218\u021a\u0005"+
		"\u0016\u0000\u0000\u0219\u021b\u0003V+\u0000\u021a\u0219\u0001\u0000\u0000"+
		"\u0000\u021a\u021b\u0001\u0000\u0000\u0000\u021b\u021c\u0001\u0000\u0000"+
		"\u0000\u021c\u021d\u0005+\u0000\u0000\u021dS\u0001\u0000\u0000\u0000\u021e"+
		"\u0221\u0003V+\u0000\u021f\u0220\u0005,\u0000\u0000\u0220\u0222\u0003"+
		"V+\u0000\u0221\u021f\u0001\u0000\u0000\u0000\u0221\u0222\u0001\u0000\u0000"+
		"\u0000\u0222\u0223\u0001\u0000\u0000\u0000\u0223\u0224\u0005+\u0000\u0000"+
		"\u0224U\u0001\u0000\u0000\u0000\u0225\u0226\u0003X,\u0000\u0226W\u0001"+
		"\u0000\u0000\u0000\u0227\u022c\u0003Z-\u0000\u0228\u0229\u00051\u0000"+
		"\u0000\u0229\u022b\u0003Z-\u0000\u022a\u0228\u0001\u0000\u0000\u0000\u022b"+
		"\u022e\u0001\u0000\u0000\u0000\u022c\u022a\u0001\u0000\u0000\u0000\u022c"+
		"\u022d\u0001\u0000\u0000\u0000\u022dY\u0001\u0000\u0000\u0000\u022e\u022c"+
		"\u0001\u0000\u0000\u0000\u022f\u0234\u0003\\.\u0000\u0230\u0231\u0005"+
		"A\u0000\u0000\u0231\u0233\u0003\\.\u0000\u0232\u0230\u0001\u0000\u0000"+
		"\u0000\u0233\u0236\u0001\u0000\u0000\u0000\u0234\u0232\u0001\u0000\u0000"+
		"\u0000\u0234\u0235\u0001\u0000\u0000\u0000\u0235[\u0001\u0000\u0000\u0000"+
		"\u0236\u0234\u0001\u0000\u0000\u0000\u0237\u023c\u0003^/\u0000\u0238\u0239"+
		"\u0005@\u0000\u0000\u0239\u023b\u0003^/\u0000\u023a\u0238\u0001\u0000"+
		"\u0000\u0000\u023b\u023e\u0001\u0000\u0000\u0000\u023c\u023a\u0001\u0000"+
		"\u0000\u0000\u023c\u023d\u0001\u0000\u0000\u0000\u023d]\u0001\u0000\u0000"+
		"\u0000\u023e\u023c\u0001\u0000\u0000\u0000\u023f\u0244\u0003`0\u0000\u0240"+
		"\u0241\u0007\u0002\u0000\u0000\u0241\u0243\u0003`0\u0000\u0242\u0240\u0001"+
		"\u0000\u0000\u0000\u0243\u0246\u0001\u0000\u0000\u0000\u0244\u0242\u0001"+
		"\u0000\u0000\u0000\u0244\u0245\u0001\u0000\u0000\u0000\u0245_\u0001\u0000"+
		"\u0000\u0000\u0246\u0244\u0001\u0000\u0000\u0000\u0247\u024b\u0003d2\u0000"+
		"\u0248\u024a\u0003b1\u0000\u0249\u0248\u0001\u0000\u0000\u0000\u024a\u024d"+
		"\u0001\u0000\u0000\u0000\u024b\u0249\u0001\u0000\u0000\u0000\u024b\u024c"+
		"\u0001\u0000\u0000\u0000\u024ca\u0001\u0000\u0000\u0000\u024d\u024b\u0001"+
		"\u0000\u0000\u0000\u024e\u024f\u0007\u0003\u0000\u0000\u024f\u0255\u0003"+
		"d2\u0000\u0250\u0251\u0005\u001e\u0000\u0000\u0251\u0255\u0003&\u0013"+
		"\u0000\u0252\u0253\u0005\u001f\u0000\u0000\u0253\u0255\u0003&\u0013\u0000"+
		"\u0254\u024e\u0001\u0000\u0000\u0000\u0254\u0250\u0001\u0000\u0000\u0000"+
		"\u0254\u0252\u0001\u0000\u0000\u0000\u0255c\u0001\u0000\u0000\u0000\u0256"+
		"\u025b\u0003f3\u0000\u0257\u0258\u0007\u0004\u0000\u0000\u0258\u025a\u0003"+
		"f3\u0000\u0259\u0257\u0001\u0000\u0000\u0000\u025a\u025d\u0001\u0000\u0000"+
		"\u0000\u025b\u0259\u0001\u0000\u0000\u0000\u025b\u025c\u0001\u0000\u0000"+
		"\u0000\u025ce\u0001\u0000\u0000\u0000\u025d\u025b\u0001\u0000\u0000\u0000"+
		"\u025e\u0263\u0003h4\u0000\u025f\u0260\u0007\u0005\u0000\u0000\u0260\u0262"+
		"\u0003h4\u0000\u0261\u025f\u0001\u0000\u0000\u0000\u0262\u0265\u0001\u0000"+
		"\u0000\u0000\u0263\u0261\u0001\u0000\u0000\u0000\u0263\u0264\u0001\u0000"+
		"\u0000\u0000\u0264g\u0001\u0000\u0000\u0000\u0265\u0263\u0001\u0000\u0000"+
		"\u0000\u0266\u0267\u0007\u0006\u0000\u0000\u0267\u026a\u0003h4\u0000\u0268"+
		"\u026a\u0003j5\u0000\u0269\u0266\u0001\u0000\u0000\u0000\u0269\u0268\u0001"+
		"\u0000\u0000\u0000\u026ai\u0001\u0000\u0000\u0000\u026b\u026f\u0003l6"+
		"\u0000\u026c\u026e\u0003~?\u0000\u026d\u026c\u0001\u0000\u0000\u0000\u026e"+
		"\u0271\u0001\u0000\u0000\u0000\u026f\u026d\u0001\u0000\u0000\u0000\u026f"+
		"\u0270\u0001\u0000\u0000\u0000\u0270k\u0001\u0000\u0000\u0000\u0271\u026f"+
		"\u0001\u0000\u0000\u0000\u0272\u0279\u0003\u0086C\u0000\u0273\u0279\u0003"+
		"v;\u0000\u0274\u0279\u0003x<\u0000\u0275\u0279\u0003z=\u0000\u0276\u0279"+
		"\u0003n7\u0000\u0277\u0279\u0003|>\u0000\u0278\u0272\u0001\u0000\u0000"+
		"\u0000\u0278\u0273\u0001\u0000\u0000\u0000\u0278\u0274\u0001\u0000\u0000"+
		"\u0000\u0278\u0275\u0001\u0000\u0000\u0000\u0278\u0276\u0001\u0000\u0000"+
		"\u0000\u0278\u0277\u0001\u0000\u0000\u0000\u0279m\u0001\u0000\u0000\u0000"+
		"\u027a\u027b\u0005\u0017\u0000\u0000\u027b\u027c\u0003V+\u0000\u027c\u0281"+
		"\u0005)\u0000\u0000\u027d\u0280\u0003p8\u0000\u027e\u0280\u0005+\u0000"+
		"\u0000\u027f\u027d\u0001\u0000\u0000\u0000\u027f\u027e\u0001\u0000\u0000"+
		"\u0000\u0280\u0283\u0001\u0000\u0000\u0000\u0281\u027f\u0001\u0000\u0000"+
		"\u0000\u0281\u0282\u0001\u0000\u0000\u0000\u0282\u0284\u0001\u0000\u0000"+
		"\u0000\u0283\u0281\u0001\u0000\u0000\u0000\u0284\u0285\u0005*\u0000\u0000"+
		"\u0285o\u0001\u0000\u0000\u0000\u0286\u0287\u0003r9\u0000\u0287\u0288"+
		"\u0005\u0018\u0000\u0000\u0288\u0289\u0003V+\u0000\u0289q\u0001\u0000"+
		"\u0000\u0000\u028a\u0292\u0005!\u0000\u0000\u028b\u028c\u0005-\u0000\u0000"+
		"\u028c\u0293\u0003&\u0013\u0000\u028d\u028f\u0005\'\u0000\u0000\u028e"+
		"\u0290\u0003t:\u0000\u028f\u028e\u0001\u0000\u0000\u0000\u028f\u0290\u0001"+
		"\u0000\u0000\u0000\u0290\u0291\u0001\u0000\u0000\u0000\u0291\u0293\u0005"+
		"(\u0000\u0000\u0292\u028b\u0001\u0000\u0000\u0000\u0292\u028d\u0001\u0000"+
		"\u0000\u0000\u0292\u0293\u0001\u0000\u0000\u0000\u0293s\u0001\u0000\u0000"+
		"\u0000\u0294\u0299\u0003r9\u0000\u0295\u0296\u0005.\u0000\u0000\u0296"+
		"\u0298\u0003r9\u0000\u0297\u0295\u0001\u0000\u0000\u0000\u0298\u029b\u0001"+
		"\u0000\u0000\u0000\u0299\u0297\u0001\u0000\u0000\u0000\u0299\u029a\u0001"+
		"\u0000\u0000\u0000\u029au\u0001\u0000\u0000\u0000\u029b\u0299\u0001\u0000"+
		"\u0000\u0000\u029c\u029d\u0005\'\u0000\u0000\u029d\u029e\u0003V+\u0000"+
		"\u029e\u029f\u0005(\u0000\u0000\u029fw\u0001\u0000\u0000\u0000\u02a0\u02a1"+
		"\u0005\f\u0000\u0000\u02a1y\u0001\u0000\u0000\u0000\u02a2\u02a3\u0005"+
		"\r\u0000\u0000\u02a3{\u0001\u0000\u0000\u0000\u02a4\u02a5\u0005!\u0000"+
		"\u0000\u02a5}\u0001\u0000\u0000\u0000\u02a6\u02a9\u0003\u0080@\u0000\u02a7"+
		"\u02a9\u0003\u0082A\u0000\u02a8\u02a6\u0001\u0000\u0000\u0000\u02a8\u02a7"+
		"\u0001\u0000\u0000\u0000\u02a9\u007f\u0001\u0000\u0000\u0000\u02aa\u02ab"+
		"\u0007\u0007\u0000\u0000\u02ab\u02ac\u0005!\u0000\u0000\u02ac\u0081\u0001"+
		"\u0000\u0000\u0000\u02ad\u02af\u0005\'\u0000\u0000\u02ae\u02b0\u0003\u0084"+
		"B\u0000\u02af\u02ae\u0001\u0000\u0000\u0000\u02af\u02b0\u0001\u0000\u0000"+
		"\u0000\u02b0\u02b1\u0001\u0000\u0000\u0000\u02b1\u02b2\u0005(\u0000\u0000"+
		"\u02b2\u0083\u0001\u0000\u0000\u0000\u02b3\u02b8\u0003V+\u0000\u02b4\u02b5"+
		"\u0005.\u0000\u0000\u02b5\u02b7\u0003V+\u0000\u02b6\u02b4\u0001\u0000"+
		"\u0000\u0000\u02b7\u02ba\u0001\u0000\u0000\u0000\u02b8\u02b6\u0001\u0000"+
		"\u0000\u0000\u02b8\u02b9\u0001\u0000\u0000\u0000\u02b9\u0085\u0001\u0000"+
		"\u0000\u0000\u02ba\u02b8\u0001\u0000\u0000\u0000\u02bb\u02c4\u0003\u0088"+
		"D\u0000\u02bc\u02c4\u0003\u008aE\u0000\u02bd\u02c4\u0003\u008cF\u0000"+
		"\u02be\u02c4\u0003\u008eG\u0000\u02bf\u02c4\u0003\u0090H\u0000\u02c0\u02c4"+
		"\u0003\u0092I\u0000\u02c1\u02c4\u0003\u0094J\u0000\u02c2\u02c4\u0003\u0096"+
		"K\u0000\u02c3\u02bb\u0001\u0000\u0000\u0000\u02c3\u02bc\u0001\u0000\u0000"+
		"\u0000\u02c3\u02bd\u0001\u0000\u0000\u0000\u02c3\u02be\u0001\u0000\u0000"+
		"\u0000\u02c3\u02bf\u0001\u0000\u0000\u0000\u02c3\u02c0\u0001\u0000\u0000"+
		"\u0000\u02c3\u02c1\u0001\u0000\u0000\u0000\u02c3\u02c2\u0001\u0000\u0000"+
		"\u0000\u02c4\u0087\u0001\u0000\u0000\u0000\u02c5\u02c6\u0005\"\u0000\u0000"+
		"\u02c6\u0089\u0001\u0000\u0000\u0000\u02c7\u02c8\u0005#\u0000\u0000\u02c8"+
		"\u008b\u0001\u0000\u0000\u0000\u02c9\u02ca\u0005$\u0000\u0000\u02ca\u008d"+
		"\u0001\u0000\u0000\u0000\u02cb\u02cc\u0005 \u0000\u0000\u02cc\u008f\u0001"+
		"\u0000\u0000\u0000\u02cd\u02ce\u0005%\u0000\u0000\u02ce\u0091\u0001\u0000"+
		"\u0000\u0000\u02cf\u02d0\u0005&\u0000\u0000\u02d0\u0093\u0001\u0000\u0000"+
		"\u0000\u02d1\u02d2\u0005F\u0000\u0000\u02d2\u0095\u0001\u0000\u0000\u0000"+
		"\u02d3\u02d4\u0005\u001d\u0000\u0000\u02d4\u0097\u0001\u0000\u0000\u0000"+
		"Q\u009d\u009f\u00a7\u00ab\u00b3\u00b6\u00bb\u00bf\u00c3\u00c8\u00ca\u00d2"+
		"\u00d6\u00db\u00dd\u00e5\u00ea\u00ec\u00f4\u00f7\u00fd\u0102\u0106\u0110"+
		"\u0114\u0120\u0127\u0130\u0137\u013d\u0141\u0150\u0157\u0161\u016e\u0175"+
		"\u0178\u0180\u0188\u018a\u0198\u019e\u01ac\u01b2\u01bd\u01c1\u01c5\u01cc"+
		"\u01da\u01dc\u01e7\u01ed\u01ef\u01f6\u01f8\u01fd\u0202\u0208\u0210\u021a"+
		"\u0221\u022c\u0234\u023c\u0244\u024b\u0254\u025b\u0263\u0269\u026f\u0278"+
		"\u027f\u0281\u028f\u0292\u0299\u02a8\u02af\u02b8\u02c3";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
