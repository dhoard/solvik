/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast;

/** Kinds of Solvik syntax-AST nodes, used for dispatch and structural test assertions. */
public enum AstKind {
    COMPILATION_UNIT,
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
