// Copyright (c) 2026-present Douglas Hoard
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package diagnostic

// Error code registry.
// Each code has a category prefix and a stable number.
// Deprecated codes are never removed, only superseded.

const (
	// Lexer errors (L)
	CodeLexerUnexpectedChar   = "L001" // unexpected character
	CodeLexerUnterminatedStr  = "L002" // unterminated string literal
	CodeLexerUnterminatedChar = "L003" // unterminated char literal
	CodeLexerInvalidEscape    = "L004" // invalid escape sequence
	CodeLexerInvalidNumber    = "L005" // invalid numeric literal

	// Parser errors (P)
	CodeParserExpectedModule    = "P001" // expected module name after 'package'
	CodeParserExpectedFunc      = "P002" // expected function declaration or import
	CodeParserExpectedImport    = "P003" // expected module name in import
	CodeParserExpectedFuncName  = "P004" // expected function name
	CodeParserExpectedLParen    = "P005" // expected '(' after function name
	CodeParserExpectedRParen    = "P006" // expected ')' after parameters
	CodeParserExpectedLBrace    = "P007" // expected '{' for function body
	CodeParserExpectedParam     = "P008" // expected parameter name
	CodeParserExpectedColon     = "P009" // expected ':' after parameter name
	CodeParserExpectedParamType = "P010" // expected parameter type
	CodeParserExpectedElemType  = "P011" // expected element type in List<T>
	CodeParserExpectedGt        = "P012" // expected '>' after List element type
	CodeParserExpectedKeyType   = "P013" // expected key type in Map<K,V>
	CodeParserExpectedComma     = "P014" // expected ',' between key and value types
	CodeParserExpectedValType   = "P015" // expected value type in Map<K,V>
	CodeParserExpectedMapGt     = "P016" // expected '>' after Map types
	CodeParserExpectedType      = "P017" // expected type before '?'
	CodeParserExpectedRBrace    = "P018" // expected '}' to close block
	CodeParserNestedFunc        = "P019" // nested functions not supported
	CodeParserExpectedDeclColon = "P020" // expected ':' after variable name
	CodeParserExpectedDeclType  = "P021" // expected type in variable declaration

	// If/while/for body errors
	CodeParserIfBody    = "P024" // expected '{' for if body
	CodeParserElseBody  = "P025" // expected 'if' or '{' after 'else'
	CodeParserWhileBody = "P028" // expected '{' for while body
	CodeParserForVar    = "P030" // expected loop variable name
	CodeParserForIn     = "P031" // expected 'in' after loop variable
	CodeParserForBody   = "P033" // expected '{' for for body
	CodeParserForKeyVar = "P046" // expected key variable name
	CodeParserForComma  = "P046" // expected ',' after key variable
	CodeParserForValVar = "P047" // expected value variable name
	CodeParserForRParen = "P048" // expected ')' after for variables

	// Expression errors
	CodeParserInvalidInt           = "P035" // invalid integer literal
	CodeParserInvalidLong          = "P036" // invalid long literal
	CodeParserInvalidFloat         = "P037" // invalid float literal
	CodeParserInvalidDouble        = "P038" // invalid double literal
	CodeParserExpectedRParenExpr   = "P039" // expected ')' after expression
	CodeParserExpectedRParenArgs   = "P040" // expected ')' after arguments
	CodeParserExpectedRBracket     = "P041" // expected ']' after index
	CodeParserExpectedRBracketList = "P042" // expected ']' to close list literal
	CodeParserExpectedColonMap     = "P043" // expected ':' after map key
	CodeParserExpectedRBraceMap    = "P044" // expected '}' to close map literal

	// Switch errors
	CodeParserSwitchBody    = "P050" // expected '{' after switch expression
	CodeParserCaseColon     = "P051" // expected ':' after case expression
	CodeParserDupDefault    = "P052" // duplicate default clause in switch
	CodeParserDefColon      = "P053" // expected ':' after default
	CodeParserExpectedCase  = "P054" // expected 'case' or 'default' in switch
	CodeParserSwitchClose   = "P055" // expected '}' to close switch
	CodeParserTooManyErrors = "P045" // too many parse errors

	// Assignment errors
	CodeParserAssignTarget = "P034" // cannot assign to non-identifier or non-index expression

	// Parser try/catch/finally/throw errors
	CodeParserTryBody     = "P056" // expected '{' for try body
	CodeParserTryClause   = "P057" // try statement requires catch or finally
	CodeParserCatchOrder  = "P058" // catch must appear before finally
	CodeParserCatchLParen = "P059" // expected '(' after catch
	CodeParserCatchParam  = "P060" // expected parameter name in catch
	CodeParserCatchColon  = "P061" // expected ':' after catch parameter name
	CodeParserCatchType   = "P062" // catch parameter must have type exception
	CodeParserCatchTypeEx = "P063" // expected type in catch parameter
	CodeParserCatchRParen = "P064" // expected ')' after catch parameter
	CodeParserCatchBody   = "P065" // expected '{' for catch body
	CodeParserFinallyBody = "P066" // expected '{' for finally body
	CodeParserThrowExpr   = "P067" // expected expression after throw
	CodeParserMutNoDecl   = "P068" // mut requires a variable declaration

	// Resolver errors (R)
	CodeResolverUndeclared   = "R001" // undeclared identifier
	CodeResolverDuplicate    = "R002" // duplicate declaration
	CodeResolverNotDefined   = "R003" // variable not defined
	CodeResolverUndeclaredID = "R004" // undeclared identifier
	CodeResolverNotModule    = "R005" // not a module
	CodeResolverFuncNotFound = "R006" // function not found

	// Checker errors (C)
	CodeCheckerTypeMismatch      = "C001" // type mismatch
	CodeCheckerCannotAssign      = "C002" // cannot assign
	CodeCheckerMissingReturn     = "C003" // missing return statement
	CodeCheckerUnreachableCode   = "C004" // unreachable code
	CodeCheckerDefiniteAssign    = "C005" // variable may not have been assigned
	CodeCheckerBreakOutside      = "C006" // break outside loop
	CodeCheckerContinueOutside   = "C007" // continue outside loop
	CodeCheckerNullAssignment    = "C008" // cannot assign null to non-nullable type
	CodeCheckerInvalidMapKey     = "C009" // invalid map key type
	CodeCheckerInvalidIndex      = "C010" // cannot index non-indexable type
	CodeCheckerNotAFunction      = "C022" // called expression is not a function
	CodeCheckerArgCount          = "C023" // expected N arguments but got M
	CodeCheckerArgType           = "C024" // argument type mismatch
	CodeCheckerFuncNotFound      = "C025" // function not found
	CodeCheckerNotBool           = "C028" // condition must be bool
	CodeCheckerDuplicateParam    = "C029" // duplicate parameter name
	CodeCheckerMainReturn        = "C031" // main must return int or void
	CodeCheckerDefAssignVar      = "C032" // variable may not have been assigned
	CodeCheckerNullToNonNull     = "C033" // cannot assign null to non-nullable type
	CodeCheckerInvalidMapKey2    = "C034" // invalid map key type
	CodeCheckerListAssign        = "C035" // cannot assign to list element
	CodeCheckerMapKeyAssign      = "C036" // cannot use as map key
	CodeCheckerMapValAssign      = "C037" // cannot assign to map value
	CodeCheckerIndexAssign       = "C038" // cannot index-assign
	CodeCheckerMapUnpack         = "C039" // (key, value) unpacking requires a Map
	CodeCheckerMemberAccess      = "C040" // cannot access member
	CodeCheckerModuleMember      = "C041" // module has no member
	CodeCheckerCatchParamType    = "C042" // catch parameter must have type exception
	CodeCheckerThrowExprType     = "C043" // throw expression must have type exception
	CodeCheckerThrowNullable     = "C044" // cannot throw nullable exception
	CodeCheckerImmutableReassign = "C045" // cannot assign to immutable variable

	// Runtime exception codes (E)
	CodeRuntimeUncaught    = "E040" // uncaught exception
	CodeRuntimeExcFieldErr = "E041" // invalid exception field access
)

// CodeCategory returns the category of an error code.
func CodeCategory(code string) string {
	if len(code) < 1 {
		return "unknown"
	}
	switch code[0] {
	case 'L':
		return "lexer"
	case 'P':
		return "parser"
	case 'R':
		return "resolver"
	case 'C':
		return "checker"
	case 'E':
		return "runtime"
	default:
		return "unknown"
	}
}
