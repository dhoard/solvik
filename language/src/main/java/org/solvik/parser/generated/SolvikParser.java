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
		FUNC=1, CLASS=2, INTERFACE=3, ENUM=4, SEALED=5, DELEGATE=6, IMPLEMENTS=7, 
		OPEN=8, EXTENDS=9, OVERRIDE=10, THIS=11, SUPER=12, VAL=13, VAR=14, IF=15, 
		ELSE=16, WHILE=17, FOR=18, BREAK=19, CONTINUE=20, RETURN=21, MATCH=22, 
		ARROW=23, SWITCH=24, CASE=25, DEFAULT=26, REGEX_KW=27, NULL=28, IS=29, 
		AS=30, BOOL_LITERAL=31, Identifier=32, INT_LITERAL=33, LONG_LITERAL=34, 
		FLOATING_LITERAL=35, CHAR_LITERAL=36, STRING_LITERAL=37, LPAREN=38, RPAREN=39, 
		LBRACE=40, RBRACE=41, SEMI=42, ASSIGN=43, COLON=44, COMMA=45, DOT=46, 
		NULLABLE_DOT=47, NULL_COALESCE=48, QUESTION=49, LBRACKET=50, RBRACKET=51, 
		ADD=52, SUB=53, MUL=54, DIV=55, BANG=56, EQ=57, NEQ=58, LT=59, LE=60, 
		GT=61, GE=62, AND=63, OR=64, WS=65, NEWLINE=66, LINE_COMMENT=67, BLOCK_COMMENT=68, 
		RAW_STRING_LITERAL=69;
	public static final int
		RULE_compilationUnit = 0, RULE_functionDecl = 1, RULE_classDecl = 2, RULE_interfaceDecl = 3, 
		RULE_enumDecl = 4, RULE_enumVariant = 5, RULE_interfaceMember = 6, RULE_signatureDecl = 7, 
		RULE_defaultMethodDecl = 8, RULE_typeRefList = 9, RULE_classMember = 10, 
		RULE_delegateDecl = 11, RULE_methodDecl = 12, RULE_methodModifier = 13, 
		RULE_propertyDecl = 14, RULE_constructorDecl = 15, RULE_parameterList = 16, 
		RULE_parameter = 17, RULE_typeParameterList = 18, RULE_typeRef = 19, RULE_typeArguments = 20, 
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
			"propertyDecl", "constructorDecl", "parameterList", "parameter", "typeParameterList", 
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
			null, "'func'", "'class'", "'interface'", "'enum'", "'sealed'", "'delegate'", 
			"'implements'", "'open'", "'extends'", "'override'", "'this'", "'super'", 
			"'val'", "'var'", "'if'", "'else'", "'while'", "'for'", "'break'", "'continue'", 
			"'return'", "'match'", "'=>'", "'switch'", "'case'", "'default'", "'regex'", 
			"'null'", "'is'", "'as'", null, null, null, null, null, null, null, "'('", 
			"')'", "'{'", "'}'", "';'", "'='", "':'", "','", "'.'", "'?.'", "'??'", 
			"'?'", "'['", "']'", "'+'", "'-'", "'*'", "'/'", "'!'", "'=='", "'!='", 
			"'<'", "'<='", "'>'", "'>='", "'&&'", "'||'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "FUNC", "CLASS", "INTERFACE", "ENUM", "SEALED", "DELEGATE", "IMPLEMENTS", 
			"OPEN", "EXTENDS", "OVERRIDE", "THIS", "SUPER", "VAL", "VAR", "IF", "ELSE", 
			"WHILE", "FOR", "BREAK", "CONTINUE", "RETURN", "MATCH", "ARROW", "SWITCH", 
			"CASE", "DEFAULT", "REGEX_KW", "NULL", "IS", "AS", "BOOL_LITERAL", "Identifier", 
			"INT_LITERAL", "LONG_LITERAL", "FLOATING_LITERAL", "CHAR_LITERAL", "STRING_LITERAL", 
			"LPAREN", "RPAREN", "LBRACE", "RBRACE", "SEMI", "ASSIGN", "COLON", "COMMA", 
			"DOT", "NULLABLE_DOT", "NULL_COALESCE", "QUESTION", "LBRACKET", "RBRACKET", 
			"ADD", "SUB", "MUL", "DIV", "BANG", "EQ", "NEQ", "LT", "LE", "GT", "GE", 
			"AND", "OR", "WS", "NEWLINE", "LINE_COMMENT", "BLOCK_COMMENT", "RAW_STRING_LITERAL"
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
			setState(160);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 81069739241044286L) != 0) || _la==RAW_STRING_LITERAL) {
				{
				setState(158);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUNC:
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
					setState(156);
					statement();
					}
					break;
				case SEMI:
					{
					setState(157);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(162);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(163);
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
		public TerminalNode FUNC() { return getToken(SolvikParser.FUNC, 0); }
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
			setState(165);
			match(FUNC);
			setState(166);
			match(Identifier);
			setState(168);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(167);
				typeParameterList();
				}
			}

			setState(170);
			match(LPAREN);
			setState(172);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(171);
				parameterList();
				}
			}

			setState(174);
			match(RPAREN);
			setState(175);
			match(COLON);
			setState(176);
			typeRef();
			setState(177);
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
			setState(180);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEALED) {
				{
				setState(179);
				match(SEALED);
				}
			}

			setState(183);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN) {
				{
				setState(182);
				match(OPEN);
				}
			}

			setState(185);
			match(CLASS);
			setState(186);
			match(Identifier);
			setState(188);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(187);
				typeParameterList();
				}
			}

			setState(192);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(190);
				match(EXTENDS);
				setState(191);
				typeRef();
				}
			}

			setState(196);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IMPLEMENTS) {
				{
				setState(194);
				match(IMPLEMENTS);
				setState(195);
				typeRefList();
				}
			}

			setState(198);
			match(LBRACE);
			setState(203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4402341504322L) != 0)) {
				{
				setState(201);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUNC:
				case DELEGATE:
				case OPEN:
				case OVERRIDE:
				case VAL:
				case VAR:
				case Identifier:
					{
					setState(199);
					classMember();
					}
					break;
				case SEMI:
					{
					setState(200);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(205);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(206);
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
			setState(208);
			match(INTERFACE);
			setState(209);
			match(Identifier);
			setState(211);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(210);
				typeParameterList();
				}
			}

			setState(215);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXTENDS) {
				{
				setState(213);
				match(EXTENDS);
				setState(214);
				typeRefList();
				}
			}

			setState(217);
			match(LBRACE);
			setState(222);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==FUNC || _la==SEMI) {
				{
				setState(220);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case FUNC:
					{
					setState(218);
					interfaceMember();
					}
					break;
				case SEMI:
					{
					setState(219);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(224);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(225);
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
			setState(227);
			match(ENUM);
			setState(228);
			match(Identifier);
			setState(230);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(229);
				typeParameterList();
				}
			}

			setState(232);
			match(LBRACE);
			setState(237);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Identifier || _la==SEMI) {
				{
				setState(235);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case Identifier:
					{
					setState(233);
					enumVariant();
					}
					break;
				case SEMI:
					{
					setState(234);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(239);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(240);
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
			setState(242);
			match(Identifier);
			setState(248);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(243);
				match(LPAREN);
				setState(245);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Identifier) {
					{
					setState(244);
					typeRefList();
					}
				}

				setState(247);
				match(RPAREN);
				}
			}

			setState(250);
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
			setState(254);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(252);
				signatureDecl();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(253);
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
		public TerminalNode FUNC() { return getToken(SolvikParser.FUNC, 0); }
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
			setState(256);
			match(FUNC);
			setState(257);
			match(Identifier);
			setState(259);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(258);
				typeParameterList();
				}
			}

			setState(261);
			match(LPAREN);
			setState(263);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(262);
				parameterList();
				}
			}

			setState(265);
			match(RPAREN);
			setState(266);
			match(COLON);
			setState(267);
			typeRef();
			setState(268);
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
		public TerminalNode FUNC() { return getToken(SolvikParser.FUNC, 0); }
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
			setState(270);
			match(FUNC);
			setState(271);
			match(Identifier);
			setState(273);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(272);
				typeParameterList();
				}
			}

			setState(275);
			match(LPAREN);
			setState(277);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(276);
				parameterList();
				}
			}

			setState(279);
			match(RPAREN);
			setState(280);
			match(COLON);
			setState(281);
			typeRef();
			setState(282);
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
			setState(284);
			typeRef();
			setState(289);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(285);
				match(COMMA);
				setState(286);
				typeRef();
				}
				}
				setState(291);
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
		public ConstructorDeclContext constructorDecl() {
			return getRuleContext(ConstructorDeclContext.class,0);
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
			setState(296);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(292);
				propertyDecl();
				}
				break;
			case DELEGATE:
				enterOuterAlt(_localctx, 2);
				{
				setState(293);
				delegateDecl();
				}
				break;
			case Identifier:
				enterOuterAlt(_localctx, 3);
				{
				setState(294);
				constructorDecl();
				}
				break;
			case FUNC:
			case OPEN:
			case OVERRIDE:
				enterOuterAlt(_localctx, 4);
				{
				setState(295);
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
			setState(298);
			match(DELEGATE);
			setState(299);
			match(VAL);
			setState(300);
			match(Identifier);
			setState(301);
			match(COLON);
			setState(302);
			typeRef();
			setState(305);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(303);
				match(ASSIGN);
				setState(304);
				expression();
				}
			}

			setState(307);
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
		public TerminalNode FUNC() { return getToken(SolvikParser.FUNC, 0); }
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
			setState(312);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OPEN || _la==OVERRIDE) {
				{
				{
				setState(309);
				methodModifier();
				}
				}
				setState(314);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(315);
			match(FUNC);
			setState(316);
			match(Identifier);
			setState(318);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LT) {
				{
				setState(317);
				typeParameterList();
				}
			}

			setState(320);
			match(LPAREN);
			setState(322);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(321);
				parameterList();
				}
			}

			setState(324);
			match(RPAREN);
			setState(325);
			match(COLON);
			setState(326);
			typeRef();
			setState(327);
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
			setState(329);
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
			setState(331);
			bindingKind();
			setState(332);
			match(Identifier);
			setState(333);
			match(COLON);
			setState(334);
			typeRef();
			setState(337);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(335);
				match(ASSIGN);
				setState(336);
				expression();
				}
			}

			setState(339);
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
	public static class ConstructorDeclContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(SolvikParser.Identifier, 0); }
		public TerminalNode LPAREN() { return getToken(SolvikParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(SolvikParser.RPAREN, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public ConstructorDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constructorDecl; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof SolvikVisitor ) return ((SolvikVisitor<? extends T>)visitor).visitConstructorDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstructorDeclContext constructorDecl() throws RecognitionException {
		ConstructorDeclContext _localctx = new ConstructorDeclContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_constructorDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(341);
			match(Identifier);
			setState(342);
			match(LPAREN);
			setState(344);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier) {
				{
				setState(343);
				parameterList();
				}
			}

			setState(346);
			match(RPAREN);
			setState(347);
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
			setState(349);
			parameter();
			setState(354);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(350);
				match(COMMA);
				setState(351);
				parameter();
				}
				}
				setState(356);
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
			setState(357);
			match(Identifier);
			setState(358);
			match(COLON);
			setState(359);
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
			setState(361);
			match(LT);
			setState(362);
			match(Identifier);
			setState(367);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(363);
				match(COMMA);
				setState(364);
				match(Identifier);
				}
				}
				setState(369);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(370);
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
			setState(372);
			match(Identifier);
			setState(374);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				{
				setState(373);
				typeArguments();
				}
				break;
			}
			setState(377);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==QUESTION) {
				{
				setState(376);
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
			setState(379);
			match(LT);
			setState(380);
			typeRef();
			setState(385);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(381);
				match(COMMA);
				setState(382);
				typeRef();
				}
				}
				setState(387);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(388);
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
			setState(390);
			match(LBRACE);
			setState(395);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((((_la - 11)) & ~0x3f) == 0 && ((1L << (_la - 11)) & 288269960985325535L) != 0)) {
				{
				setState(393);
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
					setState(391);
					statement();
					}
					break;
				case SEMI:
					{
					setState(392);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(397);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(398);
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
			setState(409);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(400);
				localDecl();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 2);
				{
				setState(401);
				ifStmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 3);
				{
				setState(402);
				whileStmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 4);
				{
				setState(403);
				forStmt();
				}
				break;
			case SWITCH:
				enterOuterAlt(_localctx, 5);
				{
				setState(404);
				switchStmt();
				}
				break;
			case BREAK:
				enterOuterAlt(_localctx, 6);
				{
				setState(405);
				breakStmt();
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 7);
				{
				setState(406);
				continueStmt();
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 8);
				{
				setState(407);
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
				setState(408);
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
			setState(411);
			bindingKind();
			setState(412);
			match(Identifier);
			setState(415);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(413);
				match(COLON);
				setState(414);
				typeRef();
				}
			}

			setState(417);
			match(ASSIGN);
			setState(418);
			expression();
			setState(419);
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
			setState(421);
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
			setState(423);
			match(IF);
			setState(424);
			match(LPAREN);
			setState(425);
			expression();
			setState(426);
			match(RPAREN);
			setState(427);
			block();
			setState(429);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(428);
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
			setState(435);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(431);
				match(ELSE);
				setState(432);
				ifStmt();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(433);
				match(ELSE);
				setState(434);
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
			setState(437);
			match(WHILE);
			setState(438);
			match(LPAREN);
			setState(439);
			expression();
			setState(440);
			match(RPAREN);
			setState(441);
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
			setState(443);
			match(FOR);
			setState(444);
			match(LPAREN);
			setState(446);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 11)) & ~0x3f) == 0 && ((1L << (_la - 11)) & 288269958837831695L) != 0)) {
				{
				setState(445);
				forInit();
				}
			}

			setState(448);
			match(SEMI);
			setState(450);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 11)) & ~0x3f) == 0 && ((1L << (_la - 11)) & 288269958837831683L) != 0)) {
				{
				setState(449);
				forCondition();
				}
			}

			setState(452);
			match(SEMI);
			setState(454);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 11)) & ~0x3f) == 0 && ((1L << (_la - 11)) & 288269958837831683L) != 0)) {
				{
				setState(453);
				forUpdate();
				}
			}

			setState(456);
			match(RPAREN);
			setState(457);
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
			setState(461);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VAL:
			case VAR:
				enterOuterAlt(_localctx, 1);
				{
				setState(459);
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
				setState(460);
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
			setState(463);
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
			setState(465);
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
			setState(467);
			match(SWITCH);
			setState(468);
			match(LPAREN);
			setState(469);
			expression();
			setState(470);
			match(RPAREN);
			setState(471);
			match(LBRACE);
			setState(477);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4398147174400L) != 0)) {
				{
				setState(475);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case CASE:
					{
					setState(472);
					switchCase();
					}
					break;
				case DEFAULT:
					{
					setState(473);
					defaultCase();
					}
					break;
				case SEMI:
					{
					setState(474);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(479);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(480);
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
			setState(482);
			match(CASE);
			setState(483);
			caseLabel();
			setState(488);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(484);
				match(COMMA);
				setState(485);
				caseLabel();
				}
				}
				setState(490);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(491);
			match(COLON);
			setState(496);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(494);
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
						setState(492);
						statement();
						}
						break;
					case SEMI:
						{
						setState(493);
						match(SEMI);
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					} 
				}
				setState(498);
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
			setState(499);
			match(DEFAULT);
			setState(500);
			match(COLON);
			setState(505);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(503);
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
						setState(501);
						statement();
						}
						break;
					case SEMI:
						{
						setState(502);
						match(SEMI);
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					} 
				}
				setState(507);
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
			setState(510);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case REGEX_KW:
				enterOuterAlt(_localctx, 1);
				{
				setState(508);
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
				setState(509);
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
			setState(512);
			match(REGEX_KW);
			setState(515);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING_LITERAL:
				{
				setState(513);
				stringLiteral();
				}
				break;
			case RAW_STRING_LITERAL:
				{
				setState(514);
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
			setState(517);
			bindingKind();
			setState(518);
			match(Identifier);
			setState(521);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(519);
				match(COLON);
				setState(520);
				typeRef();
				}
			}

			setState(523);
			match(ASSIGN);
			setState(524);
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
			setState(526);
			expression();
			setState(529);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(527);
				match(ASSIGN);
				setState(528);
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
			setState(531);
			match(BREAK);
			setState(532);
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
			setState(534);
			match(CONTINUE);
			setState(535);
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
			setState(537);
			match(RETURN);
			setState(539);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 11)) & ~0x3f) == 0 && ((1L << (_la - 11)) & 288269958837831683L) != 0)) {
				{
				setState(538);
				expression();
				}
			}

			setState(541);
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
			setState(543);
			expression();
			setState(546);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(544);
				match(ASSIGN);
				setState(545);
				expression();
				}
			}

			setState(548);
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
			setState(550);
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
			setState(552);
			logicalOr();
			setState(557);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NULL_COALESCE) {
				{
				{
				setState(553);
				match(NULL_COALESCE);
				setState(554);
				logicalOr();
				}
				}
				setState(559);
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
			setState(560);
			logicalAnd();
			setState(565);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(561);
				match(OR);
				setState(562);
				logicalAnd();
				}
				}
				setState(567);
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
			setState(568);
			equality();
			setState(573);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(569);
				match(AND);
				setState(570);
				equality();
				}
				}
				setState(575);
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
			setState(576);
			relational();
			setState(581);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EQ || _la==NEQ) {
				{
				{
				setState(577);
				_la = _input.LA(1);
				if ( !(_la==EQ || _la==NEQ) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(578);
				relational();
				}
				}
				setState(583);
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
			setState(584);
			additive();
			setState(588);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 8646911286161965056L) != 0)) {
				{
				{
				setState(585);
				relation();
				}
				}
				setState(590);
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
			setState(597);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LT:
			case LE:
			case GT:
			case GE:
				enterOuterAlt(_localctx, 1);
				{
				setState(591);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 8646911284551352320L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(592);
				additive();
				}
				break;
			case IS:
				enterOuterAlt(_localctx, 2);
				{
				setState(593);
				match(IS);
				setState(594);
				typeRef();
				}
				break;
			case AS:
				enterOuterAlt(_localctx, 3);
				{
				setState(595);
				match(AS);
				setState(596);
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
			setState(599);
			multiplicative();
			setState(604);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==SUB) {
				{
				{
				setState(600);
				_la = _input.LA(1);
				if ( !(_la==ADD || _la==SUB) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(601);
				multiplicative();
				}
				}
				setState(606);
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
			setState(607);
			unary();
			setState(612);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==MUL || _la==DIV) {
				{
				{
				setState(608);
				_la = _input.LA(1);
				if ( !(_la==MUL || _la==DIV) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(609);
				unary();
				}
				}
				setState(614);
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
			setState(618);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SUB:
			case BANG:
				enterOuterAlt(_localctx, 1);
				{
				setState(615);
				_la = _input.LA(1);
				if ( !(_la==SUB || _la==BANG) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(616);
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
				setState(617);
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
			setState(620);
			primary();
			setState(624);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 211381110439936L) != 0)) {
				{
				{
				setState(621);
				suffix();
				}
				}
				setState(626);
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
			setState(633);
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
				setState(627);
				literal();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(628);
				paren();
				}
				break;
			case THIS:
				enterOuterAlt(_localctx, 3);
				{
				setState(629);
				thisExpr();
				}
				break;
			case SUPER:
				enterOuterAlt(_localctx, 4);
				{
				setState(630);
				superExpr();
				}
				break;
			case MATCH:
				enterOuterAlt(_localctx, 5);
				{
				setState(631);
				matchExpr();
				}
				break;
			case Identifier:
				enterOuterAlt(_localctx, 6);
				{
				setState(632);
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
			setState(635);
			match(MATCH);
			setState(636);
			expression();
			setState(637);
			match(LBRACE);
			setState(642);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Identifier || _la==SEMI) {
				{
				setState(640);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case Identifier:
					{
					setState(638);
					matchBranch();
					}
					break;
				case SEMI:
					{
					setState(639);
					match(SEMI);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(644);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(645);
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
			setState(647);
			pattern();
			setState(648);
			match(ARROW);
			setState(649);
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
			setState(651);
			match(Identifier);
			setState(659);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(652);
				match(COLON);
				setState(653);
				typeRef();
				}
				break;
			case LPAREN:
				{
				setState(654);
				match(LPAREN);
				setState(656);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Identifier) {
					{
					setState(655);
					patternList();
					}
				}

				setState(658);
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
			setState(661);
			pattern();
			setState(666);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(662);
				match(COMMA);
				setState(663);
				pattern();
				}
				}
				setState(668);
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
			setState(669);
			match(LPAREN);
			setState(670);
			expression();
			setState(671);
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
			setState(673);
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
			setState(675);
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
			setState(677);
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
			setState(681);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DOT:
			case NULLABLE_DOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(679);
				memberSuffix();
				}
				break;
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(680);
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
			setState(683);
			_la = _input.LA(1);
			if ( !(_la==DOT || _la==NULLABLE_DOT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(684);
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
			setState(686);
			match(LPAREN);
			setState(688);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 11)) & ~0x3f) == 0 && ((1L << (_la - 11)) & 288269958837831683L) != 0)) {
				{
				setState(687);
				argumentList();
				}
			}

			setState(690);
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
			setState(692);
			expression();
			setState(697);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(693);
				match(COMMA);
				setState(694);
				expression();
				}
				}
				setState(699);
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
			setState(708);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(700);
				intLiteral();
				}
				break;
			case LONG_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(701);
				longLiteral();
				}
				break;
			case FLOATING_LITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(702);
				floatingLiteral();
				}
				break;
			case BOOL_LITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(703);
				boolLiteral();
				}
				break;
			case CHAR_LITERAL:
				enterOuterAlt(_localctx, 5);
				{
				setState(704);
				charLiteral();
				}
				break;
			case STRING_LITERAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(705);
				stringLiteral();
				}
				break;
			case RAW_STRING_LITERAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(706);
				rawStringLiteral();
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 8);
				{
				setState(707);
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
			setState(710);
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
			setState(712);
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
			setState(714);
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
			setState(716);
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
			setState(718);
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
			setState(720);
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
			setState(722);
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
			setState(724);
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
		"\u0004\u0001E\u02d7\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
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
		"K\u0007K\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0005\u0000\u009f\b\u0000\n\u0000\f\u0000\u00a2\t\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u00a9\b\u0001"+
		"\u0001\u0001\u0001\u0001\u0003\u0001\u00ad\b\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0003\u0002\u00b5\b\u0002"+
		"\u0001\u0002\u0003\u0002\u00b8\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0003\u0002\u00bd\b\u0002\u0001\u0002\u0001\u0002\u0003\u0002\u00c1\b"+
		"\u0002\u0001\u0002\u0001\u0002\u0003\u0002\u00c5\b\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0005\u0002\u00ca\b\u0002\n\u0002\f\u0002\u00cd\t\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003"+
		"\u00d4\b\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u00d8\b\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0005\u0003\u00dd\b\u0003\n\u0003\f\u0003"+
		"\u00e0\t\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0003\u0004\u00e7\b\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004"+
		"\u00ec\b\u0004\n\u0004\f\u0004\u00ef\t\u0004\u0001\u0004\u0001\u0004\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0003\u0005\u00f6\b\u0005\u0001\u0005\u0003"+
		"\u0005\u00f9\b\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0003"+
		"\u0006\u00ff\b\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u0104"+
		"\b\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u0108\b\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001"+
		"\b\u0003\b\u0112\b\b\u0001\b\u0001\b\u0003\b\u0116\b\b\u0001\b\u0001\b"+
		"\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0005\t\u0120\b\t\n\t"+
		"\f\t\u0123\t\t\u0001\n\u0001\n\u0001\n\u0001\n\u0003\n\u0129\b\n\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0003\u000b\u0132\b\u000b\u0001\u000b\u0001\u000b\u0001\f\u0005"+
		"\f\u0137\b\f\n\f\f\f\u013a\t\f\u0001\f\u0001\f\u0001\f\u0003\f\u013f\b"+
		"\f\u0001\f\u0001\f\u0003\f\u0143\b\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001"+
		"\f\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0003\u000e\u0152\b\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000f\u0001\u000f\u0001\u000f\u0003\u000f\u0159\b\u000f\u0001\u000f\u0001"+
		"\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0005\u0010\u0161"+
		"\b\u0010\n\u0010\f\u0010\u0164\t\u0010\u0001\u0011\u0001\u0011\u0001\u0011"+
		"\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0005\u0012"+
		"\u016e\b\u0012\n\u0012\f\u0012\u0171\t\u0012\u0001\u0012\u0001\u0012\u0001"+
		"\u0013\u0001\u0013\u0003\u0013\u0177\b\u0013\u0001\u0013\u0003\u0013\u017a"+
		"\b\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0005\u0014\u0180"+
		"\b\u0014\n\u0014\f\u0014\u0183\t\u0014\u0001\u0014\u0001\u0014\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0005\u0015\u018a\b\u0015\n\u0015\f\u0015\u018d"+
		"\t\u0015\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001"+
		"\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0003"+
		"\u0016\u019a\b\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0003"+
		"\u0017\u01a0\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0018\u0001\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001"+
		"\u0019\u0001\u0019\u0003\u0019\u01ae\b\u0019\u0001\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001a\u0003\u001a\u01b4\b\u001a\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001"+
		"\u001c\u0003\u001c\u01bf\b\u001c\u0001\u001c\u0001\u001c\u0003\u001c\u01c3"+
		"\b\u001c\u0001\u001c\u0001\u001c\u0003\u001c\u01c7\b\u001c\u0001\u001c"+
		"\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d\u0003\u001d\u01ce\b\u001d"+
		"\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f\u0001 \u0001 \u0001 "+
		"\u0001 \u0001 \u0001 \u0001 \u0001 \u0005 \u01dc\b \n \f \u01df\t \u0001"+
		" \u0001 \u0001!\u0001!\u0001!\u0001!\u0005!\u01e7\b!\n!\f!\u01ea\t!\u0001"+
		"!\u0001!\u0001!\u0005!\u01ef\b!\n!\f!\u01f2\t!\u0001\"\u0001\"\u0001\""+
		"\u0001\"\u0005\"\u01f8\b\"\n\"\f\"\u01fb\t\"\u0001#\u0001#\u0003#\u01ff"+
		"\b#\u0001$\u0001$\u0001$\u0003$\u0204\b$\u0001%\u0001%\u0001%\u0001%\u0003"+
		"%\u020a\b%\u0001%\u0001%\u0001%\u0001&\u0001&\u0001&\u0003&\u0212\b&\u0001"+
		"\'\u0001\'\u0001\'\u0001(\u0001(\u0001(\u0001)\u0001)\u0003)\u021c\b)"+
		"\u0001)\u0001)\u0001*\u0001*\u0001*\u0003*\u0223\b*\u0001*\u0001*\u0001"+
		"+\u0001+\u0001,\u0001,\u0001,\u0005,\u022c\b,\n,\f,\u022f\t,\u0001-\u0001"+
		"-\u0001-\u0005-\u0234\b-\n-\f-\u0237\t-\u0001.\u0001.\u0001.\u0005.\u023c"+
		"\b.\n.\f.\u023f\t.\u0001/\u0001/\u0001/\u0005/\u0244\b/\n/\f/\u0247\t"+
		"/\u00010\u00010\u00050\u024b\b0\n0\f0\u024e\t0\u00011\u00011\u00011\u0001"+
		"1\u00011\u00011\u00031\u0256\b1\u00012\u00012\u00012\u00052\u025b\b2\n"+
		"2\f2\u025e\t2\u00013\u00013\u00013\u00053\u0263\b3\n3\f3\u0266\t3\u0001"+
		"4\u00014\u00014\u00034\u026b\b4\u00015\u00015\u00055\u026f\b5\n5\f5\u0272"+
		"\t5\u00016\u00016\u00016\u00016\u00016\u00016\u00036\u027a\b6\u00017\u0001"+
		"7\u00017\u00017\u00017\u00057\u0281\b7\n7\f7\u0284\t7\u00017\u00017\u0001"+
		"8\u00018\u00018\u00018\u00019\u00019\u00019\u00019\u00019\u00039\u0291"+
		"\b9\u00019\u00039\u0294\b9\u0001:\u0001:\u0001:\u0005:\u0299\b:\n:\f:"+
		"\u029c\t:\u0001;\u0001;\u0001;\u0001;\u0001<\u0001<\u0001=\u0001=\u0001"+
		">\u0001>\u0001?\u0001?\u0003?\u02aa\b?\u0001@\u0001@\u0001@\u0001A\u0001"+
		"A\u0003A\u02b1\bA\u0001A\u0001A\u0001B\u0001B\u0001B\u0005B\u02b8\bB\n"+
		"B\fB\u02bb\tB\u0001C\u0001C\u0001C\u0001C\u0001C\u0001C\u0001C\u0001C"+
		"\u0003C\u02c5\bC\u0001D\u0001D\u0001E\u0001E\u0001F\u0001F\u0001G\u0001"+
		"G\u0001H\u0001H\u0001I\u0001I\u0001J\u0001J\u0001K\u0001K\u0001K\u0000"+
		"\u0000L\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtvxz|~\u0080"+
		"\u0082\u0084\u0086\u0088\u008a\u008c\u008e\u0090\u0092\u0094\u0096\u0000"+
		"\b\u0002\u0000\b\b\n\n\u0001\u0000\r\u000e\u0001\u00009:\u0001\u0000;"+
		">\u0001\u000045\u0001\u000067\u0002\u00005588\u0001\u0000./\u02f5\u0000"+
		"\u00a0\u0001\u0000\u0000\u0000\u0002\u00a5\u0001\u0000\u0000\u0000\u0004"+
		"\u00b4\u0001\u0000\u0000\u0000\u0006\u00d0\u0001\u0000\u0000\u0000\b\u00e3"+
		"\u0001\u0000\u0000\u0000\n\u00f2\u0001\u0000\u0000\u0000\f\u00fe\u0001"+
		"\u0000\u0000\u0000\u000e\u0100\u0001\u0000\u0000\u0000\u0010\u010e\u0001"+
		"\u0000\u0000\u0000\u0012\u011c\u0001\u0000\u0000\u0000\u0014\u0128\u0001"+
		"\u0000\u0000\u0000\u0016\u012a\u0001\u0000\u0000\u0000\u0018\u0138\u0001"+
		"\u0000\u0000\u0000\u001a\u0149\u0001\u0000\u0000\u0000\u001c\u014b\u0001"+
		"\u0000\u0000\u0000\u001e\u0155\u0001\u0000\u0000\u0000 \u015d\u0001\u0000"+
		"\u0000\u0000\"\u0165\u0001\u0000\u0000\u0000$\u0169\u0001\u0000\u0000"+
		"\u0000&\u0174\u0001\u0000\u0000\u0000(\u017b\u0001\u0000\u0000\u0000*"+
		"\u0186\u0001\u0000\u0000\u0000,\u0199\u0001\u0000\u0000\u0000.\u019b\u0001"+
		"\u0000\u0000\u00000\u01a5\u0001\u0000\u0000\u00002\u01a7\u0001\u0000\u0000"+
		"\u00004\u01b3\u0001\u0000\u0000\u00006\u01b5\u0001\u0000\u0000\u00008"+
		"\u01bb\u0001\u0000\u0000\u0000:\u01cd\u0001\u0000\u0000\u0000<\u01cf\u0001"+
		"\u0000\u0000\u0000>\u01d1\u0001\u0000\u0000\u0000@\u01d3\u0001\u0000\u0000"+
		"\u0000B\u01e2\u0001\u0000\u0000\u0000D\u01f3\u0001\u0000\u0000\u0000F"+
		"\u01fe\u0001\u0000\u0000\u0000H\u0200\u0001\u0000\u0000\u0000J\u0205\u0001"+
		"\u0000\u0000\u0000L\u020e\u0001\u0000\u0000\u0000N\u0213\u0001\u0000\u0000"+
		"\u0000P\u0216\u0001\u0000\u0000\u0000R\u0219\u0001\u0000\u0000\u0000T"+
		"\u021f\u0001\u0000\u0000\u0000V\u0226\u0001\u0000\u0000\u0000X\u0228\u0001"+
		"\u0000\u0000\u0000Z\u0230\u0001\u0000\u0000\u0000\\\u0238\u0001\u0000"+
		"\u0000\u0000^\u0240\u0001\u0000\u0000\u0000`\u0248\u0001\u0000\u0000\u0000"+
		"b\u0255\u0001\u0000\u0000\u0000d\u0257\u0001\u0000\u0000\u0000f\u025f"+
		"\u0001\u0000\u0000\u0000h\u026a\u0001\u0000\u0000\u0000j\u026c\u0001\u0000"+
		"\u0000\u0000l\u0279\u0001\u0000\u0000\u0000n\u027b\u0001\u0000\u0000\u0000"+
		"p\u0287\u0001\u0000\u0000\u0000r\u028b\u0001\u0000\u0000\u0000t\u0295"+
		"\u0001\u0000\u0000\u0000v\u029d\u0001\u0000\u0000\u0000x\u02a1\u0001\u0000"+
		"\u0000\u0000z\u02a3\u0001\u0000\u0000\u0000|\u02a5\u0001\u0000\u0000\u0000"+
		"~\u02a9\u0001\u0000\u0000\u0000\u0080\u02ab\u0001\u0000\u0000\u0000\u0082"+
		"\u02ae\u0001\u0000\u0000\u0000\u0084\u02b4\u0001\u0000\u0000\u0000\u0086"+
		"\u02c4\u0001\u0000\u0000\u0000\u0088\u02c6\u0001\u0000\u0000\u0000\u008a"+
		"\u02c8\u0001\u0000\u0000\u0000\u008c\u02ca\u0001\u0000\u0000\u0000\u008e"+
		"\u02cc\u0001\u0000\u0000\u0000\u0090\u02ce\u0001\u0000\u0000\u0000\u0092"+
		"\u02d0\u0001\u0000\u0000\u0000\u0094\u02d2\u0001\u0000\u0000\u0000\u0096"+
		"\u02d4\u0001\u0000\u0000\u0000\u0098\u009f\u0003\u0002\u0001\u0000\u0099"+
		"\u009f\u0003\u0004\u0002\u0000\u009a\u009f\u0003\u0006\u0003\u0000\u009b"+
		"\u009f\u0003\b\u0004\u0000\u009c\u009f\u0003,\u0016\u0000\u009d\u009f"+
		"\u0005*\u0000\u0000\u009e\u0098\u0001\u0000\u0000\u0000\u009e\u0099\u0001"+
		"\u0000\u0000\u0000\u009e\u009a\u0001\u0000\u0000\u0000\u009e\u009b\u0001"+
		"\u0000\u0000\u0000\u009e\u009c\u0001\u0000\u0000\u0000\u009e\u009d\u0001"+
		"\u0000\u0000\u0000\u009f\u00a2\u0001\u0000\u0000\u0000\u00a0\u009e\u0001"+
		"\u0000\u0000\u0000\u00a0\u00a1\u0001\u0000\u0000\u0000\u00a1\u00a3\u0001"+
		"\u0000\u0000\u0000\u00a2\u00a0\u0001\u0000\u0000\u0000\u00a3\u00a4\u0005"+
		"\u0000\u0000\u0001\u00a4\u0001\u0001\u0000\u0000\u0000\u00a5\u00a6\u0005"+
		"\u0001\u0000\u0000\u00a6\u00a8\u0005 \u0000\u0000\u00a7\u00a9\u0003$\u0012"+
		"\u0000\u00a8\u00a7\u0001\u0000\u0000\u0000\u00a8\u00a9\u0001\u0000\u0000"+
		"\u0000\u00a9\u00aa\u0001\u0000\u0000\u0000\u00aa\u00ac\u0005&\u0000\u0000"+
		"\u00ab\u00ad\u0003 \u0010\u0000\u00ac\u00ab\u0001\u0000\u0000\u0000\u00ac"+
		"\u00ad\u0001\u0000\u0000\u0000\u00ad\u00ae\u0001\u0000\u0000\u0000\u00ae"+
		"\u00af\u0005\'\u0000\u0000\u00af\u00b0\u0005,\u0000\u0000\u00b0\u00b1"+
		"\u0003&\u0013\u0000\u00b1\u00b2\u0003*\u0015\u0000\u00b2\u0003\u0001\u0000"+
		"\u0000\u0000\u00b3\u00b5\u0005\u0005\u0000\u0000\u00b4\u00b3\u0001\u0000"+
		"\u0000\u0000\u00b4\u00b5\u0001\u0000\u0000\u0000\u00b5\u00b7\u0001\u0000"+
		"\u0000\u0000\u00b6\u00b8\u0005\b\u0000\u0000\u00b7\u00b6\u0001\u0000\u0000"+
		"\u0000\u00b7\u00b8\u0001\u0000\u0000\u0000\u00b8\u00b9\u0001\u0000\u0000"+
		"\u0000\u00b9\u00ba\u0005\u0002\u0000\u0000\u00ba\u00bc\u0005 \u0000\u0000"+
		"\u00bb\u00bd\u0003$\u0012\u0000\u00bc\u00bb\u0001\u0000\u0000\u0000\u00bc"+
		"\u00bd\u0001\u0000\u0000\u0000\u00bd\u00c0\u0001\u0000\u0000\u0000\u00be"+
		"\u00bf\u0005\t\u0000\u0000\u00bf\u00c1\u0003&\u0013\u0000\u00c0\u00be"+
		"\u0001\u0000\u0000\u0000\u00c0\u00c1\u0001\u0000\u0000\u0000\u00c1\u00c4"+
		"\u0001\u0000\u0000\u0000\u00c2\u00c3\u0005\u0007\u0000\u0000\u00c3\u00c5"+
		"\u0003\u0012\t\u0000\u00c4\u00c2\u0001\u0000\u0000\u0000\u00c4\u00c5\u0001"+
		"\u0000\u0000\u0000\u00c5\u00c6\u0001\u0000\u0000\u0000\u00c6\u00cb\u0005"+
		"(\u0000\u0000\u00c7\u00ca\u0003\u0014\n\u0000\u00c8\u00ca\u0005*\u0000"+
		"\u0000\u00c9\u00c7\u0001\u0000\u0000\u0000\u00c9\u00c8\u0001\u0000\u0000"+
		"\u0000\u00ca\u00cd\u0001\u0000\u0000\u0000\u00cb\u00c9\u0001\u0000\u0000"+
		"\u0000\u00cb\u00cc\u0001\u0000\u0000\u0000\u00cc\u00ce\u0001\u0000\u0000"+
		"\u0000\u00cd\u00cb\u0001\u0000\u0000\u0000\u00ce\u00cf\u0005)\u0000\u0000"+
		"\u00cf\u0005\u0001\u0000\u0000\u0000\u00d0\u00d1\u0005\u0003\u0000\u0000"+
		"\u00d1\u00d3\u0005 \u0000\u0000\u00d2\u00d4\u0003$\u0012\u0000\u00d3\u00d2"+
		"\u0001\u0000\u0000\u0000\u00d3\u00d4\u0001\u0000\u0000\u0000\u00d4\u00d7"+
		"\u0001\u0000\u0000\u0000\u00d5\u00d6\u0005\t\u0000\u0000\u00d6\u00d8\u0003"+
		"\u0012\t\u0000\u00d7\u00d5\u0001\u0000\u0000\u0000\u00d7\u00d8\u0001\u0000"+
		"\u0000\u0000\u00d8\u00d9\u0001\u0000\u0000\u0000\u00d9\u00de\u0005(\u0000"+
		"\u0000\u00da\u00dd\u0003\f\u0006\u0000\u00db\u00dd\u0005*\u0000\u0000"+
		"\u00dc\u00da\u0001\u0000\u0000\u0000\u00dc\u00db\u0001\u0000\u0000\u0000"+
		"\u00dd\u00e0\u0001\u0000\u0000\u0000\u00de\u00dc\u0001\u0000\u0000\u0000"+
		"\u00de\u00df\u0001\u0000\u0000\u0000\u00df\u00e1\u0001\u0000\u0000\u0000"+
		"\u00e0\u00de\u0001\u0000\u0000\u0000\u00e1\u00e2\u0005)\u0000\u0000\u00e2"+
		"\u0007\u0001\u0000\u0000\u0000\u00e3\u00e4\u0005\u0004\u0000\u0000\u00e4"+
		"\u00e6\u0005 \u0000\u0000\u00e5\u00e7\u0003$\u0012\u0000\u00e6\u00e5\u0001"+
		"\u0000\u0000\u0000\u00e6\u00e7\u0001\u0000\u0000\u0000\u00e7\u00e8\u0001"+
		"\u0000\u0000\u0000\u00e8\u00ed\u0005(\u0000\u0000\u00e9\u00ec\u0003\n"+
		"\u0005\u0000\u00ea\u00ec\u0005*\u0000\u0000\u00eb\u00e9\u0001\u0000\u0000"+
		"\u0000\u00eb\u00ea\u0001\u0000\u0000\u0000\u00ec\u00ef\u0001\u0000\u0000"+
		"\u0000\u00ed\u00eb\u0001\u0000\u0000\u0000\u00ed\u00ee\u0001\u0000\u0000"+
		"\u0000\u00ee\u00f0\u0001\u0000\u0000\u0000\u00ef\u00ed\u0001\u0000\u0000"+
		"\u0000\u00f0\u00f1\u0005)\u0000\u0000\u00f1\t\u0001\u0000\u0000\u0000"+
		"\u00f2\u00f8\u0005 \u0000\u0000\u00f3\u00f5\u0005&\u0000\u0000\u00f4\u00f6"+
		"\u0003\u0012\t\u0000\u00f5\u00f4\u0001\u0000\u0000\u0000\u00f5\u00f6\u0001"+
		"\u0000\u0000\u0000\u00f6\u00f7\u0001\u0000\u0000\u0000\u00f7\u00f9\u0005"+
		"\'\u0000\u0000\u00f8\u00f3\u0001\u0000\u0000\u0000\u00f8\u00f9\u0001\u0000"+
		"\u0000\u0000\u00f9\u00fa\u0001\u0000\u0000\u0000\u00fa\u00fb\u0005*\u0000"+
		"\u0000\u00fb\u000b\u0001\u0000\u0000\u0000\u00fc\u00ff\u0003\u000e\u0007"+
		"\u0000\u00fd\u00ff\u0003\u0010\b\u0000\u00fe\u00fc\u0001\u0000\u0000\u0000"+
		"\u00fe\u00fd\u0001\u0000\u0000\u0000\u00ff\r\u0001\u0000\u0000\u0000\u0100"+
		"\u0101\u0005\u0001\u0000\u0000\u0101\u0103\u0005 \u0000\u0000\u0102\u0104"+
		"\u0003$\u0012\u0000\u0103\u0102\u0001\u0000\u0000\u0000\u0103\u0104\u0001"+
		"\u0000\u0000\u0000\u0104\u0105\u0001\u0000\u0000\u0000\u0105\u0107\u0005"+
		"&\u0000\u0000\u0106\u0108\u0003 \u0010\u0000\u0107\u0106\u0001\u0000\u0000"+
		"\u0000\u0107\u0108\u0001\u0000\u0000\u0000\u0108\u0109\u0001\u0000\u0000"+
		"\u0000\u0109\u010a\u0005\'\u0000\u0000\u010a\u010b\u0005,\u0000\u0000"+
		"\u010b\u010c\u0003&\u0013\u0000\u010c\u010d\u0005*\u0000\u0000\u010d\u000f"+
		"\u0001\u0000\u0000\u0000\u010e\u010f\u0005\u0001\u0000\u0000\u010f\u0111"+
		"\u0005 \u0000\u0000\u0110\u0112\u0003$\u0012\u0000\u0111\u0110\u0001\u0000"+
		"\u0000\u0000\u0111\u0112\u0001\u0000\u0000\u0000\u0112\u0113\u0001\u0000"+
		"\u0000\u0000\u0113\u0115\u0005&\u0000\u0000\u0114\u0116\u0003 \u0010\u0000"+
		"\u0115\u0114\u0001\u0000\u0000\u0000\u0115\u0116\u0001\u0000\u0000\u0000"+
		"\u0116\u0117\u0001\u0000\u0000\u0000\u0117\u0118\u0005\'\u0000\u0000\u0118"+
		"\u0119\u0005,\u0000\u0000\u0119\u011a\u0003&\u0013\u0000\u011a\u011b\u0003"+
		"*\u0015\u0000\u011b\u0011\u0001\u0000\u0000\u0000\u011c\u0121\u0003&\u0013"+
		"\u0000\u011d\u011e\u0005-\u0000\u0000\u011e\u0120\u0003&\u0013\u0000\u011f"+
		"\u011d\u0001\u0000\u0000\u0000\u0120\u0123\u0001\u0000\u0000\u0000\u0121"+
		"\u011f\u0001\u0000\u0000\u0000\u0121\u0122\u0001\u0000\u0000\u0000\u0122"+
		"\u0013\u0001\u0000\u0000\u0000\u0123\u0121\u0001\u0000\u0000\u0000\u0124"+
		"\u0129\u0003\u001c\u000e\u0000\u0125\u0129\u0003\u0016\u000b\u0000\u0126"+
		"\u0129\u0003\u001e\u000f\u0000\u0127\u0129\u0003\u0018\f\u0000\u0128\u0124"+
		"\u0001\u0000\u0000\u0000\u0128\u0125\u0001\u0000\u0000\u0000\u0128\u0126"+
		"\u0001\u0000\u0000\u0000\u0128\u0127\u0001\u0000\u0000\u0000\u0129\u0015"+
		"\u0001\u0000\u0000\u0000\u012a\u012b\u0005\u0006\u0000\u0000\u012b\u012c"+
		"\u0005\r\u0000\u0000\u012c\u012d\u0005 \u0000\u0000\u012d\u012e\u0005"+
		",\u0000\u0000\u012e\u0131\u0003&\u0013\u0000\u012f\u0130\u0005+\u0000"+
		"\u0000\u0130\u0132\u0003V+\u0000\u0131\u012f\u0001\u0000\u0000\u0000\u0131"+
		"\u0132\u0001\u0000\u0000\u0000\u0132\u0133\u0001\u0000\u0000\u0000\u0133"+
		"\u0134\u0005*\u0000\u0000\u0134\u0017\u0001\u0000\u0000\u0000\u0135\u0137"+
		"\u0003\u001a\r\u0000\u0136\u0135\u0001\u0000\u0000\u0000\u0137\u013a\u0001"+
		"\u0000\u0000\u0000\u0138\u0136\u0001\u0000\u0000\u0000\u0138\u0139\u0001"+
		"\u0000\u0000\u0000\u0139\u013b\u0001\u0000\u0000\u0000\u013a\u0138\u0001"+
		"\u0000\u0000\u0000\u013b\u013c\u0005\u0001\u0000\u0000\u013c\u013e\u0005"+
		" \u0000\u0000\u013d\u013f\u0003$\u0012\u0000\u013e\u013d\u0001\u0000\u0000"+
		"\u0000\u013e\u013f\u0001\u0000\u0000\u0000\u013f\u0140\u0001\u0000\u0000"+
		"\u0000\u0140\u0142\u0005&\u0000\u0000\u0141\u0143\u0003 \u0010\u0000\u0142"+
		"\u0141\u0001\u0000\u0000\u0000\u0142\u0143\u0001\u0000\u0000\u0000\u0143"+
		"\u0144\u0001\u0000\u0000\u0000\u0144\u0145\u0005\'\u0000\u0000\u0145\u0146"+
		"\u0005,\u0000\u0000\u0146\u0147\u0003&\u0013\u0000\u0147\u0148\u0003*"+
		"\u0015\u0000\u0148\u0019\u0001\u0000\u0000\u0000\u0149\u014a\u0007\u0000"+
		"\u0000\u0000\u014a\u001b\u0001\u0000\u0000\u0000\u014b\u014c\u00030\u0018"+
		"\u0000\u014c\u014d\u0005 \u0000\u0000\u014d\u014e\u0005,\u0000\u0000\u014e"+
		"\u0151\u0003&\u0013\u0000\u014f\u0150\u0005+\u0000\u0000\u0150\u0152\u0003"+
		"V+\u0000\u0151\u014f\u0001\u0000\u0000\u0000\u0151\u0152\u0001\u0000\u0000"+
		"\u0000\u0152\u0153\u0001\u0000\u0000\u0000\u0153\u0154\u0005*\u0000\u0000"+
		"\u0154\u001d\u0001\u0000\u0000\u0000\u0155\u0156\u0005 \u0000\u0000\u0156"+
		"\u0158\u0005&\u0000\u0000\u0157\u0159\u0003 \u0010\u0000\u0158\u0157\u0001"+
		"\u0000\u0000\u0000\u0158\u0159\u0001\u0000\u0000\u0000\u0159\u015a\u0001"+
		"\u0000\u0000\u0000\u015a\u015b\u0005\'\u0000\u0000\u015b\u015c\u0003*"+
		"\u0015\u0000\u015c\u001f\u0001\u0000\u0000\u0000\u015d\u0162\u0003\"\u0011"+
		"\u0000\u015e\u015f\u0005-\u0000\u0000\u015f\u0161\u0003\"\u0011\u0000"+
		"\u0160\u015e\u0001\u0000\u0000\u0000\u0161\u0164\u0001\u0000\u0000\u0000"+
		"\u0162\u0160\u0001\u0000\u0000\u0000\u0162\u0163\u0001\u0000\u0000\u0000"+
		"\u0163!\u0001\u0000\u0000\u0000\u0164\u0162\u0001\u0000\u0000\u0000\u0165"+
		"\u0166\u0005 \u0000\u0000\u0166\u0167\u0005,\u0000\u0000\u0167\u0168\u0003"+
		"&\u0013\u0000\u0168#\u0001\u0000\u0000\u0000\u0169\u016a\u0005;\u0000"+
		"\u0000\u016a\u016f\u0005 \u0000\u0000\u016b\u016c\u0005-\u0000\u0000\u016c"+
		"\u016e\u0005 \u0000\u0000\u016d\u016b\u0001\u0000\u0000\u0000\u016e\u0171"+
		"\u0001\u0000\u0000\u0000\u016f\u016d\u0001\u0000\u0000\u0000\u016f\u0170"+
		"\u0001\u0000\u0000\u0000\u0170\u0172\u0001\u0000\u0000\u0000\u0171\u016f"+
		"\u0001\u0000\u0000\u0000\u0172\u0173\u0005=\u0000\u0000\u0173%\u0001\u0000"+
		"\u0000\u0000\u0174\u0176\u0005 \u0000\u0000\u0175\u0177\u0003(\u0014\u0000"+
		"\u0176\u0175\u0001\u0000\u0000\u0000\u0176\u0177\u0001\u0000\u0000\u0000"+
		"\u0177\u0179\u0001\u0000\u0000\u0000\u0178\u017a\u00051\u0000\u0000\u0179"+
		"\u0178\u0001\u0000\u0000\u0000\u0179\u017a\u0001\u0000\u0000\u0000\u017a"+
		"\'\u0001\u0000\u0000\u0000\u017b\u017c\u0005;\u0000\u0000\u017c\u0181"+
		"\u0003&\u0013\u0000\u017d\u017e\u0005-\u0000\u0000\u017e\u0180\u0003&"+
		"\u0013\u0000\u017f\u017d\u0001\u0000\u0000\u0000\u0180\u0183\u0001\u0000"+
		"\u0000\u0000\u0181\u017f\u0001\u0000\u0000\u0000\u0181\u0182\u0001\u0000"+
		"\u0000\u0000\u0182\u0184\u0001\u0000\u0000\u0000\u0183\u0181\u0001\u0000"+
		"\u0000\u0000\u0184\u0185\u0005=\u0000\u0000\u0185)\u0001\u0000\u0000\u0000"+
		"\u0186\u018b\u0005(\u0000\u0000\u0187\u018a\u0003,\u0016\u0000\u0188\u018a"+
		"\u0005*\u0000\u0000\u0189\u0187\u0001\u0000\u0000\u0000\u0189\u0188\u0001"+
		"\u0000\u0000\u0000\u018a\u018d\u0001\u0000\u0000\u0000\u018b\u0189\u0001"+
		"\u0000\u0000\u0000\u018b\u018c\u0001\u0000\u0000\u0000\u018c\u018e\u0001"+
		"\u0000\u0000\u0000\u018d\u018b\u0001\u0000\u0000\u0000\u018e\u018f\u0005"+
		")\u0000\u0000\u018f+\u0001\u0000\u0000\u0000\u0190\u019a\u0003.\u0017"+
		"\u0000\u0191\u019a\u00032\u0019\u0000\u0192\u019a\u00036\u001b\u0000\u0193"+
		"\u019a\u00038\u001c\u0000\u0194\u019a\u0003@ \u0000\u0195\u019a\u0003"+
		"N\'\u0000\u0196\u019a\u0003P(\u0000\u0197\u019a\u0003R)\u0000\u0198\u019a"+
		"\u0003T*\u0000\u0199\u0190\u0001\u0000\u0000\u0000\u0199\u0191\u0001\u0000"+
		"\u0000\u0000\u0199\u0192\u0001\u0000\u0000\u0000\u0199\u0193\u0001\u0000"+
		"\u0000\u0000\u0199\u0194\u0001\u0000\u0000\u0000\u0199\u0195\u0001\u0000"+
		"\u0000\u0000\u0199\u0196\u0001\u0000\u0000\u0000\u0199\u0197\u0001\u0000"+
		"\u0000\u0000\u0199\u0198\u0001\u0000\u0000\u0000\u019a-\u0001\u0000\u0000"+
		"\u0000\u019b\u019c\u00030\u0018\u0000\u019c\u019f\u0005 \u0000\u0000\u019d"+
		"\u019e\u0005,\u0000\u0000\u019e\u01a0\u0003&\u0013\u0000\u019f\u019d\u0001"+
		"\u0000\u0000\u0000\u019f\u01a0\u0001\u0000\u0000\u0000\u01a0\u01a1\u0001"+
		"\u0000\u0000\u0000\u01a1\u01a2\u0005+\u0000\u0000\u01a2\u01a3\u0003V+"+
		"\u0000\u01a3\u01a4\u0005*\u0000\u0000\u01a4/\u0001\u0000\u0000\u0000\u01a5"+
		"\u01a6\u0007\u0001\u0000\u0000\u01a61\u0001\u0000\u0000\u0000\u01a7\u01a8"+
		"\u0005\u000f\u0000\u0000\u01a8\u01a9\u0005&\u0000\u0000\u01a9\u01aa\u0003"+
		"V+\u0000\u01aa\u01ab\u0005\'\u0000\u0000\u01ab\u01ad\u0003*\u0015\u0000"+
		"\u01ac\u01ae\u00034\u001a\u0000\u01ad\u01ac\u0001\u0000\u0000\u0000\u01ad"+
		"\u01ae\u0001\u0000\u0000\u0000\u01ae3\u0001\u0000\u0000\u0000\u01af\u01b0"+
		"\u0005\u0010\u0000\u0000\u01b0\u01b4\u00032\u0019\u0000\u01b1\u01b2\u0005"+
		"\u0010\u0000\u0000\u01b2\u01b4\u0003*\u0015\u0000\u01b3\u01af\u0001\u0000"+
		"\u0000\u0000\u01b3\u01b1\u0001\u0000\u0000\u0000\u01b45\u0001\u0000\u0000"+
		"\u0000\u01b5\u01b6\u0005\u0011\u0000\u0000\u01b6\u01b7\u0005&\u0000\u0000"+
		"\u01b7\u01b8\u0003V+\u0000\u01b8\u01b9\u0005\'\u0000\u0000\u01b9\u01ba"+
		"\u0003*\u0015\u0000\u01ba7\u0001\u0000\u0000\u0000\u01bb\u01bc\u0005\u0012"+
		"\u0000\u0000\u01bc\u01be\u0005&\u0000\u0000\u01bd\u01bf\u0003:\u001d\u0000"+
		"\u01be\u01bd\u0001\u0000\u0000\u0000\u01be\u01bf\u0001\u0000\u0000\u0000"+
		"\u01bf\u01c0\u0001\u0000\u0000\u0000\u01c0\u01c2\u0005*\u0000\u0000\u01c1"+
		"\u01c3\u0003<\u001e\u0000\u01c2\u01c1\u0001\u0000\u0000\u0000\u01c2\u01c3"+
		"\u0001\u0000\u0000\u0000\u01c3\u01c4\u0001\u0000\u0000\u0000\u01c4\u01c6"+
		"\u0005*\u0000\u0000\u01c5\u01c7\u0003>\u001f\u0000\u01c6\u01c5\u0001\u0000"+
		"\u0000\u0000\u01c6\u01c7\u0001\u0000\u0000\u0000\u01c7\u01c8\u0001\u0000"+
		"\u0000\u0000\u01c8\u01c9\u0005\'\u0000\u0000\u01c9\u01ca\u0003*\u0015"+
		"\u0000\u01ca9\u0001\u0000\u0000\u0000\u01cb\u01ce\u0003J%\u0000\u01cc"+
		"\u01ce\u0003L&\u0000\u01cd\u01cb\u0001\u0000\u0000\u0000\u01cd\u01cc\u0001"+
		"\u0000\u0000\u0000\u01ce;\u0001\u0000\u0000\u0000\u01cf\u01d0\u0003V+"+
		"\u0000\u01d0=\u0001\u0000\u0000\u0000\u01d1\u01d2\u0003L&\u0000\u01d2"+
		"?\u0001\u0000\u0000\u0000\u01d3\u01d4\u0005\u0018\u0000\u0000\u01d4\u01d5"+
		"\u0005&\u0000\u0000\u01d5\u01d6\u0003V+\u0000\u01d6\u01d7\u0005\'\u0000"+
		"\u0000\u01d7\u01dd\u0005(\u0000\u0000\u01d8\u01dc\u0003B!\u0000\u01d9"+
		"\u01dc\u0003D\"\u0000\u01da\u01dc\u0005*\u0000\u0000\u01db\u01d8\u0001"+
		"\u0000\u0000\u0000\u01db\u01d9\u0001\u0000\u0000\u0000\u01db\u01da\u0001"+
		"\u0000\u0000\u0000\u01dc\u01df\u0001\u0000\u0000\u0000\u01dd\u01db\u0001"+
		"\u0000\u0000\u0000\u01dd\u01de\u0001\u0000\u0000\u0000\u01de\u01e0\u0001"+
		"\u0000\u0000\u0000\u01df\u01dd\u0001\u0000\u0000\u0000\u01e0\u01e1\u0005"+
		")\u0000\u0000\u01e1A\u0001\u0000\u0000\u0000\u01e2\u01e3\u0005\u0019\u0000"+
		"\u0000\u01e3\u01e8\u0003F#\u0000\u01e4\u01e5\u0005-\u0000\u0000\u01e5"+
		"\u01e7\u0003F#\u0000\u01e6\u01e4\u0001\u0000\u0000\u0000\u01e7\u01ea\u0001"+
		"\u0000\u0000\u0000\u01e8\u01e6\u0001\u0000\u0000\u0000\u01e8\u01e9\u0001"+
		"\u0000\u0000\u0000\u01e9\u01eb\u0001\u0000\u0000\u0000\u01ea\u01e8\u0001"+
		"\u0000\u0000\u0000\u01eb\u01f0\u0005,\u0000\u0000\u01ec\u01ef\u0003,\u0016"+
		"\u0000\u01ed\u01ef\u0005*\u0000\u0000\u01ee\u01ec\u0001\u0000\u0000\u0000"+
		"\u01ee\u01ed\u0001\u0000\u0000\u0000\u01ef\u01f2\u0001\u0000\u0000\u0000"+
		"\u01f0\u01ee\u0001\u0000\u0000\u0000\u01f0\u01f1\u0001\u0000\u0000\u0000"+
		"\u01f1C\u0001\u0000\u0000\u0000\u01f2\u01f0\u0001\u0000\u0000\u0000\u01f3"+
		"\u01f4\u0005\u001a\u0000\u0000\u01f4\u01f9\u0005,\u0000\u0000\u01f5\u01f8"+
		"\u0003,\u0016\u0000\u01f6\u01f8\u0005*\u0000\u0000\u01f7\u01f5\u0001\u0000"+
		"\u0000\u0000\u01f7\u01f6\u0001\u0000\u0000\u0000\u01f8\u01fb\u0001\u0000"+
		"\u0000\u0000\u01f9\u01f7\u0001\u0000\u0000\u0000\u01f9\u01fa\u0001\u0000"+
		"\u0000\u0000\u01faE\u0001\u0000\u0000\u0000\u01fb\u01f9\u0001\u0000\u0000"+
		"\u0000\u01fc\u01ff\u0003H$\u0000\u01fd\u01ff\u0003V+\u0000\u01fe\u01fc"+
		"\u0001\u0000\u0000\u0000\u01fe\u01fd\u0001\u0000\u0000\u0000\u01ffG\u0001"+
		"\u0000\u0000\u0000\u0200\u0203\u0005\u001b\u0000\u0000\u0201\u0204\u0003"+
		"\u0092I\u0000\u0202\u0204\u0003\u0094J\u0000\u0203\u0201\u0001\u0000\u0000"+
		"\u0000\u0203\u0202\u0001\u0000\u0000\u0000\u0204I\u0001\u0000\u0000\u0000"+
		"\u0205\u0206\u00030\u0018\u0000\u0206\u0209\u0005 \u0000\u0000\u0207\u0208"+
		"\u0005,\u0000\u0000\u0208\u020a\u0003&\u0013\u0000\u0209\u0207\u0001\u0000"+
		"\u0000\u0000\u0209\u020a\u0001\u0000\u0000\u0000\u020a\u020b\u0001\u0000"+
		"\u0000\u0000\u020b\u020c\u0005+\u0000\u0000\u020c\u020d\u0003V+\u0000"+
		"\u020dK\u0001\u0000\u0000\u0000\u020e\u0211\u0003V+\u0000\u020f\u0210"+
		"\u0005+\u0000\u0000\u0210\u0212\u0003V+\u0000\u0211\u020f\u0001\u0000"+
		"\u0000\u0000\u0211\u0212\u0001\u0000\u0000\u0000\u0212M\u0001\u0000\u0000"+
		"\u0000\u0213\u0214\u0005\u0013\u0000\u0000\u0214\u0215\u0005*\u0000\u0000"+
		"\u0215O\u0001\u0000\u0000\u0000\u0216\u0217\u0005\u0014\u0000\u0000\u0217"+
		"\u0218\u0005*\u0000\u0000\u0218Q\u0001\u0000\u0000\u0000\u0219\u021b\u0005"+
		"\u0015\u0000\u0000\u021a\u021c\u0003V+\u0000\u021b\u021a\u0001\u0000\u0000"+
		"\u0000\u021b\u021c\u0001\u0000\u0000\u0000\u021c\u021d\u0001\u0000\u0000"+
		"\u0000\u021d\u021e\u0005*\u0000\u0000\u021eS\u0001\u0000\u0000\u0000\u021f"+
		"\u0222\u0003V+\u0000\u0220\u0221\u0005+\u0000\u0000\u0221\u0223\u0003"+
		"V+\u0000\u0222\u0220\u0001\u0000\u0000\u0000\u0222\u0223\u0001\u0000\u0000"+
		"\u0000\u0223\u0224\u0001\u0000\u0000\u0000\u0224\u0225\u0005*\u0000\u0000"+
		"\u0225U\u0001\u0000\u0000\u0000\u0226\u0227\u0003X,\u0000\u0227W\u0001"+
		"\u0000\u0000\u0000\u0228\u022d\u0003Z-\u0000\u0229\u022a\u00050\u0000"+
		"\u0000\u022a\u022c\u0003Z-\u0000\u022b\u0229\u0001\u0000\u0000\u0000\u022c"+
		"\u022f\u0001\u0000\u0000\u0000\u022d\u022b\u0001\u0000\u0000\u0000\u022d"+
		"\u022e\u0001\u0000\u0000\u0000\u022eY\u0001\u0000\u0000\u0000\u022f\u022d"+
		"\u0001\u0000\u0000\u0000\u0230\u0235\u0003\\.\u0000\u0231\u0232\u0005"+
		"@\u0000\u0000\u0232\u0234\u0003\\.\u0000\u0233\u0231\u0001\u0000\u0000"+
		"\u0000\u0234\u0237\u0001\u0000\u0000\u0000\u0235\u0233\u0001\u0000\u0000"+
		"\u0000\u0235\u0236\u0001\u0000\u0000\u0000\u0236[\u0001\u0000\u0000\u0000"+
		"\u0237\u0235\u0001\u0000\u0000\u0000\u0238\u023d\u0003^/\u0000\u0239\u023a"+
		"\u0005?\u0000\u0000\u023a\u023c\u0003^/\u0000\u023b\u0239\u0001\u0000"+
		"\u0000\u0000\u023c\u023f\u0001\u0000\u0000\u0000\u023d\u023b\u0001\u0000"+
		"\u0000\u0000\u023d\u023e\u0001\u0000\u0000\u0000\u023e]\u0001\u0000\u0000"+
		"\u0000\u023f\u023d\u0001\u0000\u0000\u0000\u0240\u0245\u0003`0\u0000\u0241"+
		"\u0242\u0007\u0002\u0000\u0000\u0242\u0244\u0003`0\u0000\u0243\u0241\u0001"+
		"\u0000\u0000\u0000\u0244\u0247\u0001\u0000\u0000\u0000\u0245\u0243\u0001"+
		"\u0000\u0000\u0000\u0245\u0246\u0001\u0000\u0000\u0000\u0246_\u0001\u0000"+
		"\u0000\u0000\u0247\u0245\u0001\u0000\u0000\u0000\u0248\u024c\u0003d2\u0000"+
		"\u0249\u024b\u0003b1\u0000\u024a\u0249\u0001\u0000\u0000\u0000\u024b\u024e"+
		"\u0001\u0000\u0000\u0000\u024c\u024a\u0001\u0000\u0000\u0000\u024c\u024d"+
		"\u0001\u0000\u0000\u0000\u024da\u0001\u0000\u0000\u0000\u024e\u024c\u0001"+
		"\u0000\u0000\u0000\u024f\u0250\u0007\u0003\u0000\u0000\u0250\u0256\u0003"+
		"d2\u0000\u0251\u0252\u0005\u001d\u0000\u0000\u0252\u0256\u0003&\u0013"+
		"\u0000\u0253\u0254\u0005\u001e\u0000\u0000\u0254\u0256\u0003&\u0013\u0000"+
		"\u0255\u024f\u0001\u0000\u0000\u0000\u0255\u0251\u0001\u0000\u0000\u0000"+
		"\u0255\u0253\u0001\u0000\u0000\u0000\u0256c\u0001\u0000\u0000\u0000\u0257"+
		"\u025c\u0003f3\u0000\u0258\u0259\u0007\u0004\u0000\u0000\u0259\u025b\u0003"+
		"f3\u0000\u025a\u0258\u0001\u0000\u0000\u0000\u025b\u025e\u0001\u0000\u0000"+
		"\u0000\u025c\u025a\u0001\u0000\u0000\u0000\u025c\u025d\u0001\u0000\u0000"+
		"\u0000\u025de\u0001\u0000\u0000\u0000\u025e\u025c\u0001\u0000\u0000\u0000"+
		"\u025f\u0264\u0003h4\u0000\u0260\u0261\u0007\u0005\u0000\u0000\u0261\u0263"+
		"\u0003h4\u0000\u0262\u0260\u0001\u0000\u0000\u0000\u0263\u0266\u0001\u0000"+
		"\u0000\u0000\u0264\u0262\u0001\u0000\u0000\u0000\u0264\u0265\u0001\u0000"+
		"\u0000\u0000\u0265g\u0001\u0000\u0000\u0000\u0266\u0264\u0001\u0000\u0000"+
		"\u0000\u0267\u0268\u0007\u0006\u0000\u0000\u0268\u026b\u0003h4\u0000\u0269"+
		"\u026b\u0003j5\u0000\u026a\u0267\u0001\u0000\u0000\u0000\u026a\u0269\u0001"+
		"\u0000\u0000\u0000\u026bi\u0001\u0000\u0000\u0000\u026c\u0270\u0003l6"+
		"\u0000\u026d\u026f\u0003~?\u0000\u026e\u026d\u0001\u0000\u0000\u0000\u026f"+
		"\u0272\u0001\u0000\u0000\u0000\u0270\u026e\u0001\u0000\u0000\u0000\u0270"+
		"\u0271\u0001\u0000\u0000\u0000\u0271k\u0001\u0000\u0000\u0000\u0272\u0270"+
		"\u0001\u0000\u0000\u0000\u0273\u027a\u0003\u0086C\u0000\u0274\u027a\u0003"+
		"v;\u0000\u0275\u027a\u0003x<\u0000\u0276\u027a\u0003z=\u0000\u0277\u027a"+
		"\u0003n7\u0000\u0278\u027a\u0003|>\u0000\u0279\u0273\u0001\u0000\u0000"+
		"\u0000\u0279\u0274\u0001\u0000\u0000\u0000\u0279\u0275\u0001\u0000\u0000"+
		"\u0000\u0279\u0276\u0001\u0000\u0000\u0000\u0279\u0277\u0001\u0000\u0000"+
		"\u0000\u0279\u0278\u0001\u0000\u0000\u0000\u027am\u0001\u0000\u0000\u0000"+
		"\u027b\u027c\u0005\u0016\u0000\u0000\u027c\u027d\u0003V+\u0000\u027d\u0282"+
		"\u0005(\u0000\u0000\u027e\u0281\u0003p8\u0000\u027f\u0281\u0005*\u0000"+
		"\u0000\u0280\u027e\u0001\u0000\u0000\u0000\u0280\u027f\u0001\u0000\u0000"+
		"\u0000\u0281\u0284\u0001\u0000\u0000\u0000\u0282\u0280\u0001\u0000\u0000"+
		"\u0000\u0282\u0283\u0001\u0000\u0000\u0000\u0283\u0285\u0001\u0000\u0000"+
		"\u0000\u0284\u0282\u0001\u0000\u0000\u0000\u0285\u0286\u0005)\u0000\u0000"+
		"\u0286o\u0001\u0000\u0000\u0000\u0287\u0288\u0003r9\u0000\u0288\u0289"+
		"\u0005\u0017\u0000\u0000\u0289\u028a\u0003V+\u0000\u028aq\u0001\u0000"+
		"\u0000\u0000\u028b\u0293\u0005 \u0000\u0000\u028c\u028d\u0005,\u0000\u0000"+
		"\u028d\u0294\u0003&\u0013\u0000\u028e\u0290\u0005&\u0000\u0000\u028f\u0291"+
		"\u0003t:\u0000\u0290\u028f\u0001\u0000\u0000\u0000\u0290\u0291\u0001\u0000"+
		"\u0000\u0000\u0291\u0292\u0001\u0000\u0000\u0000\u0292\u0294\u0005\'\u0000"+
		"\u0000\u0293\u028c\u0001\u0000\u0000\u0000\u0293\u028e\u0001\u0000\u0000"+
		"\u0000\u0293\u0294\u0001\u0000\u0000\u0000\u0294s\u0001\u0000\u0000\u0000"+
		"\u0295\u029a\u0003r9\u0000\u0296\u0297\u0005-\u0000\u0000\u0297\u0299"+
		"\u0003r9\u0000\u0298\u0296\u0001\u0000\u0000\u0000\u0299\u029c\u0001\u0000"+
		"\u0000\u0000\u029a\u0298\u0001\u0000\u0000\u0000\u029a\u029b\u0001\u0000"+
		"\u0000\u0000\u029bu\u0001\u0000\u0000\u0000\u029c\u029a\u0001\u0000\u0000"+
		"\u0000\u029d\u029e\u0005&\u0000\u0000\u029e\u029f\u0003V+\u0000\u029f"+
		"\u02a0\u0005\'\u0000\u0000\u02a0w\u0001\u0000\u0000\u0000\u02a1\u02a2"+
		"\u0005\u000b\u0000\u0000\u02a2y\u0001\u0000\u0000\u0000\u02a3\u02a4\u0005"+
		"\f\u0000\u0000\u02a4{\u0001\u0000\u0000\u0000\u02a5\u02a6\u0005 \u0000"+
		"\u0000\u02a6}\u0001\u0000\u0000\u0000\u02a7\u02aa\u0003\u0080@\u0000\u02a8"+
		"\u02aa\u0003\u0082A\u0000\u02a9\u02a7\u0001\u0000\u0000\u0000\u02a9\u02a8"+
		"\u0001\u0000\u0000\u0000\u02aa\u007f\u0001\u0000\u0000\u0000\u02ab\u02ac"+
		"\u0007\u0007\u0000\u0000\u02ac\u02ad\u0005 \u0000\u0000\u02ad\u0081\u0001"+
		"\u0000\u0000\u0000\u02ae\u02b0\u0005&\u0000\u0000\u02af\u02b1\u0003\u0084"+
		"B\u0000\u02b0\u02af\u0001\u0000\u0000\u0000\u02b0\u02b1\u0001\u0000\u0000"+
		"\u0000\u02b1\u02b2\u0001\u0000\u0000\u0000\u02b2\u02b3\u0005\'\u0000\u0000"+
		"\u02b3\u0083\u0001\u0000\u0000\u0000\u02b4\u02b9\u0003V+\u0000\u02b5\u02b6"+
		"\u0005-\u0000\u0000\u02b6\u02b8\u0003V+\u0000\u02b7\u02b5\u0001\u0000"+
		"\u0000\u0000\u02b8\u02bb\u0001\u0000\u0000\u0000\u02b9\u02b7\u0001\u0000"+
		"\u0000\u0000\u02b9\u02ba\u0001\u0000\u0000\u0000\u02ba\u0085\u0001\u0000"+
		"\u0000\u0000\u02bb\u02b9\u0001\u0000\u0000\u0000\u02bc\u02c5\u0003\u0088"+
		"D\u0000\u02bd\u02c5\u0003\u008aE\u0000\u02be\u02c5\u0003\u008cF\u0000"+
		"\u02bf\u02c5\u0003\u008eG\u0000\u02c0\u02c5\u0003\u0090H\u0000\u02c1\u02c5"+
		"\u0003\u0092I\u0000\u02c2\u02c5\u0003\u0094J\u0000\u02c3\u02c5\u0003\u0096"+
		"K\u0000\u02c4\u02bc\u0001\u0000\u0000\u0000\u02c4\u02bd\u0001\u0000\u0000"+
		"\u0000\u02c4\u02be\u0001\u0000\u0000\u0000\u02c4\u02bf\u0001\u0000\u0000"+
		"\u0000\u02c4\u02c0\u0001\u0000\u0000\u0000\u02c4\u02c1\u0001\u0000\u0000"+
		"\u0000\u02c4\u02c2\u0001\u0000\u0000\u0000\u02c4\u02c3\u0001\u0000\u0000"+
		"\u0000\u02c5\u0087\u0001\u0000\u0000\u0000\u02c6\u02c7\u0005!\u0000\u0000"+
		"\u02c7\u0089\u0001\u0000\u0000\u0000\u02c8\u02c9\u0005\"\u0000\u0000\u02c9"+
		"\u008b\u0001\u0000\u0000\u0000\u02ca\u02cb\u0005#\u0000\u0000\u02cb\u008d"+
		"\u0001\u0000\u0000\u0000\u02cc\u02cd\u0005\u001f\u0000\u0000\u02cd\u008f"+
		"\u0001\u0000\u0000\u0000\u02ce\u02cf\u0005$\u0000\u0000\u02cf\u0091\u0001"+
		"\u0000\u0000\u0000\u02d0\u02d1\u0005%\u0000\u0000\u02d1\u0093\u0001\u0000"+
		"\u0000\u0000\u02d2\u02d3\u0005E\u0000\u0000\u02d3\u0095\u0001\u0000\u0000"+
		"\u0000\u02d4\u02d5\u0005\u001c\u0000\u0000\u02d5\u0097\u0001\u0000\u0000"+
		"\u0000Q\u009e\u00a0\u00a8\u00ac\u00b4\u00b7\u00bc\u00c0\u00c4\u00c9\u00cb"+
		"\u00d3\u00d7\u00dc\u00de\u00e6\u00eb\u00ed\u00f5\u00f8\u00fe\u0103\u0107"+
		"\u0111\u0115\u0121\u0128\u0131\u0138\u013e\u0142\u0151\u0158\u0162\u016f"+
		"\u0176\u0179\u0181\u0189\u018b\u0199\u019f\u01ad\u01b3\u01be\u01c2\u01c6"+
		"\u01cd\u01db\u01dd\u01e8\u01ee\u01f0\u01f7\u01f9\u01fe\u0203\u0209\u0211"+
		"\u021b\u0222\u022d\u0235\u023d\u0245\u024c\u0255\u025c\u0264\u026a\u0270"+
		"\u0279\u0280\u0282\u0290\u0293\u029a\u02a9\u02b0\u02b9\u02c4";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
