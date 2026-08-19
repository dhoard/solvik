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
// Every code identifies one error class and is stable. Codes are never reused:
// a retired code is left in place with a note, and new error classes receive a
// fresh code. Codes are unique per class even when several distinct messages
// share a class (e.g. C068, the mutating-method family).
//
// The comment on each constant is the primary message emitted with the code.

const (
	// Lexer errors (L)

	CodeLexerUnexpectedChar   = "L001" // unexpected character: %q
	CodeLexerBadExponent      = "L002" // expected digit in exponent
	CodeLexerBadHexDigits     = "L003" // expected hex digits after 0x
	CodeLexerUnterminatedStr  = "L004" // unterminated string literal
	CodeLexerNewlineInString  = "L005" // newline in string literal
	CodeLexerUnterminatedRaw  = "L006" // unterminated raw string literal, expected %s
	CodeLexerUnterminatedChar = "L007" // unterminated char literal
	CodeLexerCharExpectedEnd  = "L008" // unterminated char literal (expected ')
	CodeLexerUnterminatedBlk  = "L009" // unterminated block comment
	CodeLexerRawDelimTooLarge = "L010" // raw string delimiter too large: maximum is 255 '#' characters
	CodeLexerBadUnderscore    = "L011" // invalid underscore placement in numeric literal
	CodeLexerDoubleSuffix     = "L012" // 'D' suffix is no longer supported; floating-point literals have type float
	CodeLexerLongSuffix       = "L013" // 'L' suffix is no longer supported; integer literals have type int
	CodeLexerBadPrefixDigits  = "L014" // expected digits after 0x/0b/0o prefix
	CodeLexerBadUTF8Char      = "L015" // invalid UTF-8 encoding in char literal
	CodeLexerUnknownEscape    = "L016" // unknown escape sequence '\\%c'
	CodeLexerBadEscapeHex     = "L017" // invalid hexadecimal digits in \x escape
	CodeLexerBadUnicodeEscape = "L018" // invalid hexadecimal digits or code point in \u/\U escape

	// Parser errors (P)

	CodeParserExpectedModule      = "P001" // expected module name after 'package'
	CodeParserExpectedDecl        = "P002" // expected function declaration or use
	CodeParserExpectedFuncName    = "P004" // expected function name
	CodeParserExpectedFuncLParen  = "P005" // expected '(' after function name
	CodeParserExpectedFuncRParen  = "P006" // expected ')' after parameters
	CodeParserExpectedFuncBody    = "P007" // expected '{' for function body
	CodeParserExpectedParamName   = "P008" // expected parameter name
	CodeParserExpectedParamColon  = "P009" // expected ':' after parameter name
	CodeParserExpectedParamType   = "P010" // expected parameter type
	CodeParserExpectedListElem    = "P011" // expected element type in List<T>
	CodeParserExpectedListGt      = "P012" // expected '>' after List element type
	CodeParserExpectedMapKeyType  = "P013" // expected key type in Map<K,V>
	CodeParserExpectedMapComma    = "P014" // expected ',' between key and value types
	CodeParserExpectedMapValType  = "P015" // expected value type in Map<K,V>
	CodeParserExpectedMapGt       = "P016" // expected '>' after Map types
	CodeParserExpectedType        = "P017" // expected type before '?'
	CodeParserExpectedBlockEnd    = "P018" // expected '}' to close block
	CodeParserNestedFunc          = "P019" // nested functions not supported
	CodeParserExpectedDeclColon   = "P020" // expected ':' after variable name
	CodeParserExpectedDeclType    = "P021" // expected type in variable declaration
	CodeParserIfBody              = "P024" // expected '{' for if body
	CodeParserElseBody            = "P025" // expected 'if' or '{' after 'else'
	CodeParserWhileBody           = "P028" // expected '{' for while body
	CodeParserForVar              = "P030" // expected loop variable name
	CodeParserForIn               = "P031" // expected 'in' after loop variable
	CodeParserForBody             = "P033" // expected '{' for for body
	CodeParserAssignTarget        = "P034" // cannot assign to non-identifier or non-index expression
	CodeParserInvalidInt          = "P035" // invalid integer literal: %s
	CodeParserInvalidFloat        = "P037" // invalid float literal: %s
	CodeParserExpectedRParenExpr  = "P039" // expected ')' after expression
	CodeParserExpectedRParenArgs  = "P040" // expected ')' after arguments
	CodeParserExpectedRBracket    = "P041" // expected ']' after index
	CodeParserExpectedRBracketLst = "P042" // expected ']' to close list literal
	CodeParserExpectedMapColon    = "P043" // expected ':' after map key
	CodeParserExpectedMapClose    = "P044" // expected '}' to close map literal
	CodeParserTooManyErrors       = "P045" // too many parse errors; stopping
	CodeParserForComma            = "P046" // expected ',' after key variable in for
	CodeParserForValVar           = "P047" // expected value variable name in for
	CodeParserRequiresPackage     = "P048" // file must start with a 'package' declaration
	CodeParserDupPackage          = "P049" // duplicate 'package' declaration
	CodeParserUseAfterFunc        = "P050" // 'use' declaration must appear before any function declaration
	CodeParserCaseColon           = "P051" // case labels do not use ':'; expected '{' after case expression
	CodeParserDupDefault          = "P052" // duplicate default clause in switch
	CodeParserDefColon            = "P053" // default labels do not use ':'; expected '{'
	CodeParserExpectedCase        = "P054" // expected 'case' or 'default' in switch
	CodeParserSwitchClose         = "P055" // expected '}' to close switch
	CodeParserTryBody             = "P056" // expected '{' for try body
	CodeParserTryClause           = "P057" // try statement requires catch or finally
	CodeParserCatchOrder          = "P058" // catch must appear before finally
	CodeParserCatchLParen         = "P059" // expected '(' after catch
	CodeParserCatchParam          = "P060" // expected parameter name in catch
	CodeParserCatchColon          = "P061" // expected ':' after catch parameter name
	CodeParserCatchTypeEx         = "P063" // expected type in catch parameter
	CodeParserCatchRParen         = "P064" // expected ')' after catch parameter
	CodeParserCatchBody           = "P065" // expected '{' for catch body
	CodeParserFinallyBody         = "P066" // expected '{' for finally body
	CodeParserThrowExpr           = "P067" // expected expression after throw
	CodeParserMutNoDecl           = "P068" // mut requires a variable declaration
	CodeParserCaseBody            = "P069" // expected '{' for case body
	CodeParserMultiReturnTypes    = "P070" // multiple return types are not supported; use a struct type instead
	CodeParserUnknownLong         = "P071" // unknown type 'long'; use 'int', which is a signed 64-bit integer
	CodeParserTraitFunc           = "P072" // expected 'func' in trait declaration
	CodeParserStatementSeparator  = "P073" // expected newline or ';' between statements
	CodeParserBareReturnArrow     = "P074" // invalid bare or void return arrow
	CodeParserMapBindingParens    = "P075" // map iteration bindings do not use parentheses; use 'for key, value in map'
	CodeParserEnumName            = "P076" // expected enum name
	CodeParserStructName          = "P077" // expected struct name
	CodeParserTraitName           = "P078" // expected trait name
	CodeParserMethodName          = "P079" // expected method name
	CodeParserMethodLParen        = "P080" // expected '(' after method name
	CodeParserEnumLBrace          = "P081" // expected '{' after enum name
	CodeParserStructLBrace        = "P082" // expected '{' after struct name
	CodeParserTraitLBrace         = "P083" // expected '{' after trait name
	CodeParserEnumClose           = "P084" // expected '}' after enum variants
	CodeParserStructClose         = "P085" // expected '}' after struct fields
	CodeParserTraitClose          = "P086" // expected '}' after trait methods
	CodeParserFieldColon          = "P087" // expected ':' after field name
	CodeParserFieldType           = "P088" // expected field type
	CodeParserStackElem           = "P089" // expected element type in Stack<T>
	CodeParserStackGt             = "P090" // expected '>' after Stack element type
	CodeParserExtraTypeGt         = "P091" // unexpected extra '>' in type
	CodeParserForKeyVar           = "P092" // expected key variable name in for
	CodeParserStructLiteralClose  = "P094" // expected '}' to close struct literal
	CodeParserExpectedDotIdent    = "P095" // expected identifier after '.'
	CodeParserEnumVariantName     = "P096" // expected variant name in enum
	CodeParserEnumVariantValue    = "P097" // expected integer literal after '=' in enum variant
	CodeParserStructFieldOrFunc   = "P098" // expected field name or 'func' in struct
	CodeParserStructLiteralField  = "P099" // expected field name in struct literal
	CodeParserUseSourceType       = "P100" // expected 'url:' or 'file:' after 'use'
	CodeParserUseValue            = "P101" // expected path value after '<source-type>:'
	CodeParserUseSourceColon      = "P102" // expected ':' after source type
	CodeParserUseBadSourceType    = "P103" // expected 'url:' or 'file:', got '<type>:'
	CodeParserUseFlagName         = "P104" // expected flag name after use value
	CodeParserUseFlagColon        = "P105" // expected ':' after flag name
	CodeParserUseDupChecksum      = "P106" // duplicate 'checksum' flag
	CodeParserUseChecksumShape    = "P107" // expected checksum in the form 'sha256:<64-hex>'
	CodeParserUseBadChecksum      = "P108" // checksum must use the form 'sha256:<64-hex>'
	CodeParserUseDupInsecure      = "P109" // duplicate 'insecure' flag
	CodeParserUseInsecureValue    = "P110" // expected 'true' or 'false' after 'insecure:'
	CodeParserUseUnknownFlag      = "P111" // unknown flag '%s'
	CodeParserUseHTTPInsecure     = "P112" // http URLs require insecure:true flag
	CodeParserUseChecksumRequired = "P113" // sha-256 checksum or insecure:true is required for https URLs
	CodeParserForRParen           = "P114" // expected ')' after for variables
	CodeParserExpectedMemberName  = "P115" // expected member name after '.'
	CodeParserSwitchBody          = "P116" // expected '{' after switch expression
	CodeParserVariadicNullable    = "P117" // variadic parameter cannot be nullable
	CodeParserConcatPrefix        = "P118" // '..' is a binary string-concatenation operator and cannot be used as a prefix
	CodeParserUnknownDouble       = "P119" // unknown type 'double'; use 'float', which is a 64-bit floating-point type
	CodeParserMultiReturnValues   = "P120" // multiple return values are not supported; use a struct instead
	CodeParserConcatNoOperand     = "P121" // '..' requires a left and right operand, but the right operand is missing
	CodeParserMultiTargetAssign   = "P122" // multi-target assignment is not supported; use a struct type instead

	// Resolver errors (R)

	CodeResolverBreakOutside    = "R002" // break outside loop
	CodeResolverContinueOutside = "R003" // continue outside loop
	CodeResolverUndeclaredID    = "R004" // undeclared identifier

	// Checker errors (C)

	CodeCheckerMissingReturn     = "C001" // missing return in function '%s' returning %s
	CodeCheckerUnreachableCode   = "C002" // unreachable statement
	CodeCheckerCannotAssign      = "C003" // cannot assign %s to %s in variable declaration
	CodeCheckerBadAssignTarget   = "C004" // left side of assignment must be an identifier or index expression
	CodeCheckerUndeclared        = "C005" // undeclared variable
	CodeCheckerAssignMismatch    = "C006" // cannot assign %s to %s
	CodeCheckerIfNotBool         = "C007" // if condition must be bool
	CodeCheckerWhileNotBool      = "C008" // while condition must be bool
	CodeCheckerForIterable       = "C009" // for-in requires a List, string, or Map
	CodeCheckerReturnCount       = "C010" // function returns %d values but return statement has %d
	CodeCheckerMissingValue      = "C011" // missing return value in function returning a value
	CodeCheckerNegateNonNumeric  = "C012" // cannot negate non-numeric type
	CodeCheckerNotNonBool        = "C013" // cannot apply ! to non-bool type
	CodeCheckerBitNotNonInt      = "C014" // cannot apply ~ to non-integer type
	CodeCheckerArithType         = "C015" // cannot apply %s to %s and %s
	CodeCheckerCompareType       = "C016" // cannot compare %s and %s with ==
	CodeCheckerRelationalType    = "C017" // cannot apply %s to %s and %s
	CodeCheckerAndOrType         = "C018" // cannot apply %s to non-bool types
	CodeCheckerBitOpType         = "C019" // cannot apply %s to non-integer types
	CodeCheckerShiftType         = "C020" // shift requires integer operands
	CodeCheckerNotAFunction      = "C022" // called expression is not a function
	CodeCheckerArgCount          = "C023" // expected %d arguments but got %d
	CodeCheckerArgType           = "C024" // argument %d: expected %s but got %s
	CodeCheckerListIndexType     = "C025" // list index must be an integer
	CodeCheckerStringIndexType   = "C026" // string index must be an integer
	CodeCheckerBadIndex          = "C027" // cannot index %s
	CodeCheckerCoalesceOperand   = "C028" // operand of ?? must be a value, not void/function/module
	CodeCheckerNoMain            = "C029" // no main function found
	CodeCheckerMainParams        = "C030" // main function must not have parameters
	CodeCheckerMainReturn        = "C031" // main must return int or void
	CodeCheckerNullToNonNull     = "C032" // cannot assign null to non-nullable type %s
	CodeCheckerNullAssign        = "C033" // cannot assign null to non-nullable type %s
	CodeCheckerInvalidMapKey     = "C034" // invalid map key type: %s (allowed: bool, byte, int, char, string, enum)
	CodeCheckerListAssign        = "C035" // cannot assign %s to list element of type %s
	CodeCheckerMapKeyAssign      = "C036" // cannot use %s as map key of type %s
	CodeCheckerMapValAssign      = "C037" // cannot assign %s to map value of type %s
	CodeCheckerIndexAssign       = "C038" // cannot index-assign to %s
	CodeCheckerMapUnpack         = "C039" // key, value unpacking requires a Map
	CodeCheckerMemberAccess      = "C040" // cannot access member '%s' of %s
	CodeCheckerModuleMember      = "C041" // module '%s' has no member '%s'
	CodeCheckerCatchParamType    = "C042" // catch parameter must have type exception
	CodeCheckerThrowExprType     = "C043" // throw expression must have type exception or string
	CodeCheckerThrowNullable     = "C044" // cannot throw nullable exception
	CodeCheckerImmutableReassign = "C045" // cannot assign to immutable variable; consider adding 'mut'
	CodeCheckerDupVariantName    = "C046" // duplicate variant name '%s' in enum '%s'
	CodeCheckerDupVariantValue   = "C047" // duplicate value %d in enum '%s'
	CodeCheckerNoEnumVariant     = "C048" // enum '%s' has no variant '%s'
	CodeCheckerBadSpread         = "C055" // cannot spread %s into %s
	CodeCheckerSpreadNotList     = "C056" // spread expression must be a List
	CodeCheckerUnknownStruct     = "C060" // unknown struct type
	CodeCheckerNoStructField     = "C061" // struct '%s' has no field '%s'
	CodeCheckerDupLiteralField   = "C062" // duplicate field '%s' in struct literal
	CodeCheckerMissingField      = "C064" // missing field '%s' in struct literal
	CodeCheckerNoFieldOrMethod   = "C065" // struct '%s' has no field '%s'
	CodeCheckerMutMethod         = "C068" // mutating-method receiver rules (see messages)
	CodeCheckerFieldNonStruct    = "C069" // cannot assign to field of non-struct value
	CodeCheckerImmutableField    = "C070" // cannot assign to immutable field '%s' of struct '%s'
	CodeCheckerPrivateField      = "C071" // field '%s' of struct '%s' is private
	CodeCheckerPrivateMethod     = "C072" // method '%s' of struct '%s' is private
	CodeCheckerNoTraitMethod     = "C073" // trait '%s' has no method '%s'
	CodeCheckerStructPositional  = "C074" // struct '%s' requires named-field construction; use %s { field: value }
	CodeCheckerStackArgs         = "C075" // stack() takes no arguments
	CodeCheckerFieldAssign       = "C077" // cannot assign %s to field '%s' of type %s
	CodeCheckerReturnInVoid      = "C078" // function returns no values but return statement has %d
	CodeCheckerReturnMismatch    = "C079" // cannot return %s as value %d: expected %s
	CodeCheckerSwitchEnum        = "C080" // cannot compare %s and %s in switch
	CodeCheckerMinArgCount       = "C081" // expected at least %d arguments but got %d
	CodeCheckerListElemType      = "C082" // list element: expected %s but got %s
	CodeCheckerVariadicArgType   = "C083" // variadic argument %d: expected %s but got %s
	CodeCheckerMainMultiReturn   = "C084" // main must return at most one value (int or void)
	CodeCheckerDefAssign         = "C085" // variable '%s' may not have been assigned
	CodeCheckerExceptionMember   = "C086" // exception has no member '%s'
	CodeCheckerNoFieldOrMethod2  = "C087" // struct '%s' has no field or method '%s'
	CodeCheckerNoMethod          = "C088" // struct '%s' has no method '%s'
	CodeCheckerTraitNoMethods    = "C089" // trait '%s' has no methods
	CodeCheckerLibraryMain       = "C093" // library file cannot define main; only the entry file may
	CodeCheckerSwitchCaseType    = "C094" // switch case of type %s can never match switch of type %s
	CodeCheckerDupFunction       = "C090" // duplicate function '%s'
	CodeCheckerDupStructField    = "C091" // duplicate field '%s' in struct '%s'
	CodeCheckerDupParameter      = "C092" // duplicate parameter '%s' in function '%s'

	// Runtime exception codes (E)

	CodeRuntimeNoMain           = "E001" // no main function found
	CodeRuntimeCancelled        = "E002" // execution cancelled
	CodeRuntimeInstLimit        = "E003" // instruction limit exceeded
	CodeRuntimeFellOffEnd       = "E004" // execution fell off end of code
	CodeRuntimeDecode           = "E005" // decode error
	CodeRuntimeLocalRange       = "E006" // local index %d out of range
	CodeRuntimeLocalStore       = "E007" // local index %d out of range
	CodeRuntimeGlobalRange      = "E008" // global index %d out of range
	CodeRuntimeGlobalStore      = "E009" // global index %d out of range
	CodeRuntimeDivZero          = "E010" // integer division by zero
	CodeRuntimeModZero          = "E011" // integer modulo by zero
	CodeRuntimeBadFuncIdx       = "E014" // invalid function index: %d
	CodeRuntimeMaxCallDepth     = "E015" // maximum call depth exceeded
	CodeRuntimeBadNativeIdx     = "E016" // invalid native function index: %d
	CodeRuntimeNativeMissing    = "E017" // native function not found: %s.%s
	CodeRuntimeNativeError      = "E018" // native function failure
	CodeRuntimeBadIndex         = "E019" // cannot index non-list/non-stack
	CodeRuntimeIndexRange       = "E020" // index out of range
	CodeRuntimeIndexNonList     = "E021" // cannot index non-list
	CodeRuntimeListSetRange     = "E022" // list index out of range
	CodeRuntimeAppendNonList    = "E023" // cannot append to non-list
	CodeRuntimeListLimit        = "E024" // list size limit exceeded
	CodeRuntimeLenType          = "E025" // cannot get length of non-list/non-stack
	CodeRuntimeIndexNonMap      = "E026" // cannot index non-map
	CodeRuntimeSetNonMap        = "E027" // cannot set on non-map
	CodeRuntimeMapLimit         = "E028" // map size limit exceeded
	CodeRuntimeContainsNonMap   = "E029" // cannot check contains on non-map
	CodeRuntimeMapLenType       = "E030" // cannot get length of non-map
	CodeRuntimeNullRef          = "E031" // null reference
	CodeRuntimeUnknownOp        = "E032" // unknown opcode: %d
	CodeRuntimeMapKeysType      = "E039" // cannot get keys of non-map
	CodeRuntimeUncaught         = "E040" // uncaught exception: %s
	CodeRuntimeExcFieldErr      = "E041" // expected exception, got %s
	CodeRuntimeFieldNonStruct   = "E042" // cannot access field of non-struct value
	CodeRuntimeFieldRange       = "E043" // field index %d out of range
	CodeRuntimeStoreNonStruct   = "E044" // cannot store field of non-struct value
	CodeRuntimeStoreFieldRange  = "E045" // field index %d out of range
	CodeRuntimeTraitExpected    = "E050" // expected trait value for method invocation
	CodeRuntimeTraitMethodRange = "E051" // trait method index %d out of range
	CodeRuntimeTraitBadFunc     = "E052" // invalid function index %d for trait method
	CodeRuntimePushNonStack     = "E060" // cannot push to non-stack
	CodeRuntimePopNonStack      = "E061" // cannot pop from non-stack
	CodeRuntimePopEmpty         = "E062" // pop from empty stack
	CodeRuntimePeekNonStack     = "E063" // cannot peek from non-stack
	CodeRuntimePeekEmpty        = "E064" // peek from empty stack
	CodeRuntimeStackLenType     = "E065" // cannot get size of non-stack
	CodeRuntimeTypeMismatch     = "E066" // type mismatch: expected %s, got %s (any downcast)
)

// Retired codes. These were assigned in earlier versions and must not be
// reused; they are kept here so the numbers stay reserved.
const (
// L003: 'expected hex digits after 0x' (replaced by the generalized L014).
// P036: deprecated 'long' literal suffix (replaced by P071).
// P038: deprecated 'double' literal suffix (replaced by P119).
// P062: 'catch parameter must have type exception' (replaced by C042).
// P093: reserved for struct literal field colon (unused; P087 is used).
// R001: 'undeclared variable' in assignment (resolver); superseded by R004.
// R005, R006: reserved resolver codes (unused).
// C063: 'cannot assign X to field Y of type Z' (renumbered to C077).
// C076: reserved (unused).
// E012, E013: reserved runtime codes (unused).
// E033-E038, E046-E049, E053-E059: reserved runtime codes (unused).
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
