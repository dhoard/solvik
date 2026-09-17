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
package org.solvik.ast;

/** Kinds of Solvik syntax-AST nodes, used for dispatch and structural test assertions. */
public enum AstKind {
    COMPILATION_UNIT,
    INCLUDE_DECL,
    MODULE_DECL,
    FUNCTION_DECL,
    CLASS_DECL,
    INTERFACE_DECL,
    ENUM_DECL,
    ENUM_VARIANT,
    PROPERTY_DECL,
    DELEGATE_DECL,
    CONSTRUCTOR_DECL,
    PARAMETER,
    TYPE_REF,
    TYPE_PARAMETER,
    SIGNATURE_DECL,
    BLOCK,
    LOCAL_DECL,
    IF_STMT,
    ELSE_BRANCH,
    WHILE_STMT,
    FOR_STMT,
    FOR_IN_STMT,
    SWITCH_STMT,
    SWITCH_CASE,
    BREAK_STMT,
    CONTINUE_STMT,
    ASSIGN_STMT,
    RETURN_STMT,
    EXPR_STMT,
    UNARY_EXPR,
    BINARY_EXPR,
    CALL_EXPR,
    MEMBER_ACCESS_EXPR,
    NAMESPACE_ACCESS_EXPR,
    PAREN_EXPR,
    NAME_REF_EXPR,
    THIS_EXPR,
    SUPER_EXPR,
    TYPE_TEST_EXPR,
    CAST_EXPR,
    MATCH_EXPR,
    MATCH_BRANCH,
    CASE_LABEL,
    REGEX_CASE_LABEL,
    WILDCARD_PATTERN,
    ENUM_PATTERN,
    BINDING_PATTERN,
    INT_LITERAL,
    LONG_LITERAL,
    FLOATING_LITERAL,
    BOOL_LITERAL,
    CHAR_LITERAL,
    STRING_LITERAL,
    RAW_STRING_LITERAL,
    NULL_LITERAL
}
