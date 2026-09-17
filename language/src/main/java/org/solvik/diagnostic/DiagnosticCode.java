/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.diagnostic;

/**
 * Stable, machine-readable diagnostic codes. Codes are part of the diagnostic contract: a code,
 * once introduced, must keep identifying the same class of problem even if the human message
 * changes.
 *
 * <p>Naming convention: {@code SOLV-<layer>-<number>} where layer is one of {@code LEX},
 * {@code PARS}, {@code RESOL}, {@code TYPE}, {@code SEM}, or {@code LOWER} corresponding to the
 * compiler stages in docs/ARCHITECTURE.md. Only the layers implemented so far have codes; later
 * phases extend the enumeration rather than renumbering it.
 */
public enum DiagnosticCode {
    /** Lexical error: a character sequence could not be tokenized (includes unterminated strings). */
    LEXER_ERROR("SOLV-LEX-001"),

    /** Lexical error: a raw string reached end of input without its counted closing delimiter. */
    LEXER_UNTERMINATED_RAW_STRING("SOLV-LEX-002"),

    /** Lexical error: a normal string contains an escape sequence the language does not support. */
    LEXER_INVALID_ESCAPE("SOLV-LEX-003"),

    /** The parser encountered a token that cannot appear at this position. */
    PARSER_UNEXPECTED_TOKEN("SOLV-PARS-001"),
    /** The input ended before a construct was complete. */
    PARSER_INCOMPLETE_INPUT("SOLV-PARS-002"),
    /** SimpleLanguage-only syntax was rejected by the Solvik grammar. */
    PARSER_UNSUPPORTED_LEGACY_SYNTAX("SOLV-PARS-004"),

    /** Name resolution: no declaration is visible for the referenced name. */
    RESOL_UNKNOWN_NAME("SOLV-RESOL-001"),
    /** Name resolution: the same name is declared twice in one scope. */
    RESOL_DUPLICATE_NAME("SOLV-RESOL-002"),
    /** Name resolution: a written type name does not denote a known Solvik type. */
    RESOL_UNKNOWN_TYPE("SOLV-RESOL-003"),
    /** Name resolution: a member access names a property or method the receiver type does not declare. */
    RESOL_UNKNOWN_MEMBER("SOLV-RESOL-004"),
    /** Name resolution: {@code this} is used outside an instance method or {@code init}. */
    RESOL_THIS_OUTSIDE_CLASS("SOLV-RESOL-005"),

    /** Static typing: a value is not assignable to the required type. */
    TYPE_MISMATCH("SOLV-TYPE-001"),
    /** Static typing: a call target is not a declared function. */
    TYPE_NOT_CALLABLE("SOLV-TYPE-002"),
    /** Static typing: a call supplies the wrong number of arguments. */
    TYPE_ARITY_MISMATCH("SOLV-TYPE-003"),
    /** Static typing: an operator is applied to unsupported operand types. */
    TYPE_INVALID_OPERANDS("SOLV-TYPE-004"),
    /** Static typing: a condition does not have type {@code Boolean}. */
    TYPE_CONDITION_NOT_BOOLEAN("SOLV-TYPE-005"),
    /** Static typing: an assignment writes to an immutable binding. */
    TYPE_ASSIGN_TO_IMMUTABLE("SOLV-TYPE-006"),
    /** Static typing: an assignment target is not a mutable local. */
    TYPE_INVALID_ASSIGNMENT_TARGET("SOLV-TYPE-007"),
    /** Static typing: a variable is read before it is definitely initialized. */
    TYPE_UNINITIALIZED_VARIABLE("SOLV-TYPE-008"),
    /** Static typing: a returned value is not assignable to the declared return type. */
    TYPE_RETURN_MISMATCH("SOLV-TYPE-009"),
    /** Static typing: a bare {@code return;} appears in a value-returning function. */
    TYPE_MISSING_RETURN_VALUE("SOLV-TYPE-010"),
    /** Static typing: a value is returned from a {@code Unit} function. */
    TYPE_UNEXPECTED_RETURN_VALUE("SOLV-TYPE-011"),
    /** Static typing: a value-returning function has a path that returns no value. */
    TYPE_MISSING_RETURN_PATH("SOLV-TYPE-012"),
    /** Static typing: an integer literal is outside the signed 32-bit {@code Int} range. */
    TYPE_INT_LITERAL_OUT_OF_RANGE("SOLV-TYPE-013"),
    /** Static typing: a function name is used where a value is required. */
    TYPE_FUNCTION_AS_VALUE("SOLV-TYPE-014"),
    /**
     * Static typing: member access requires class declarations. Retained so the diagnostic code
     * remains stable; Phase 6 replaces it with {@link #RESOL_UNKNOWN_MEMBER} for every reachable
     * case and no longer emits this code.
     */
    TYPE_MEMBER_ACCESS_UNSUPPORTED("SOLV-TYPE-015"),
    /** Static typing: a class name is used where a value is required. */
    TYPE_CLASS_AS_VALUE("SOLV-TYPE-016"),
    /** Static typing: a property is read before it is definitely initialized. */
    TYPE_UNINITIALIZED_PROPERTY("SOLV-TYPE-017"),
    /** Static typing: a property without a declaration initializer is not assigned on every path. */
    TYPE_MISSING_PROPERTY_INITIALIZER("SOLV-TYPE-018"),
    /** Static typing: a {@code Long} literal is outside the signed 64-bit {@code Long} range. */
    TYPE_LONG_LITERAL_OUT_OF_RANGE("SOLV-TYPE-019"),
    /** Static typing: a character literal does not contain exactly one character or a known escape. */
    TYPE_INVALID_CHAR_LITERAL("SOLV-TYPE-020"),
    /** Static typing: an explicit numeric conversion of a constant is outside the target range. */
    TYPE_CONVERSION_OUT_OF_RANGE("SOLV-TYPE-021"),
    /** Static typing: an explicit numeric conversion receives a non-numeric operand. */
    TYPE_INVALID_CONVERSION("SOLV-TYPE-022"),
    /** Static typing: an interface name is used where a value is required. */
    TYPE_INTERFACE_AS_VALUE("SOLV-TYPE-023"),
    /** Static typing: a nullable receiver is dereferenced without a null check or safe access. */
    TYPE_NULLABLE_DEREFERENCE("SOLV-TYPE-024"),
    /** Static typing: the type operand of {@code is} or {@code as} is nullable or otherwise invalid. */
    TYPE_INVALID_TYPE_OPERAND("SOLV-TYPE-025"),
    /** Static typing: the left operand of {@code ??} is not nullable. */
    TYPE_NULLABLE_REQUIRED("SOLV-TYPE-026"),
    /** Static typing: a bare reference to a generic type omits its required type arguments. */
    TYPE_RAW_GENERIC_TYPE("SOLV-TYPE-027"),
    /** Static typing: a generic type application supplies the wrong number of type arguments. */
    TYPE_TYPE_ARGUMENT_ARITY("SOLV-TYPE-028"),
    /** Static typing: a non-generic type is applied to type arguments. */
    TYPE_NOT_GENERIC("SOLV-TYPE-029"),
    /** Static typing: a generic call's type arguments cannot be inferred from its arguments. */
    TYPE_CANNOT_INFER("SOLV-TYPE-030"),
    /** Static typing: a type test or cast targets a type argument that is erased at runtime. */
    TYPE_ERASED_TYPE_TEST("SOLV-TYPE-031"),
    /** Static typing: an enum type name is used where a value is required. */
    TYPE_ENUM_AS_VALUE("SOLV-TYPE-032"),
    /** Static typing: a {@code match} pattern is not compatible with the matched value's type. */
    TYPE_MATCH_PATTERN("SOLV-TYPE-033"),
    /** Static typing: the branch results of a {@code match} have no nearest common supertype. */
    TYPE_MATCH_RESULT("SOLV-TYPE-034"),
    /** Static typing: a constant {@code Regex} pattern is invalid or outside the portable dialect. */
    TYPE_INVALID_REGEX_PATTERN("SOLV-TYPE-035"),
    /** Static typing: a {@code switch} constant case label is not assignable to the switched type. */
    TYPE_CASE_LABEL_MISMATCH("SOLV-TYPE-036"),
    /** Static typing: a {@code switch} regex case is used where the switched value is not a String. */
    TYPE_REGEX_CASE_REQUIRES_STRING("SOLV-TYPE-037"),

    /** Name resolution: {@code super} is used outside a class or where no superclass exists. */
    RESOL_SUPER_OUTSIDE_CLASS("SOLV-RESOL-006"),

    /** Semantic validation: the executable entry point has the wrong signature. */
    SEM_INVALID_ENTRY_POINT("SOLV-SEM-001"),
    /** Semantic validation: {@code break} or {@code continue} appears outside a loop. */
    SEM_LOOP_CONTROL_OUTSIDE_LOOP("SOLV-SEM-002"),
    /** Semantic validation: a value-producing expression is used as a statement and is not a call. */
    SEM_VALUE_EXPRESSION_STATEMENT("SOLV-SEM-003"),
    /** Semantic validation: a {@code for} initializer is neither a local declaration nor an assignment. */
    SEM_FOR_INITIALIZER("SOLV-SEM-004"),
    /** Semantic validation: a {@code for} update clause is not an assignment. */
    SEM_FOR_UPDATE("SOLV-SEM-005"),
    /** Semantic validation: a class without an {@code init} has a property without an initializer. */
    SEM_CLASS_REQUIRES_INITIALIZER("SOLV-SEM-006"),
    /** Semantic validation: a class declares more than one {@code init}. */
    SEM_DUPLICATE_INIT("SOLV-SEM-007"),
    /** Semantic validation: a class extends a class that was not declared {@code open}. */
    SEM_EXTEND_FINAL("SOLV-SEM-008"),
    /** Semantic validation: an {@code extends} clause names something other than a class or {@code Object}. */
    SEM_INVALID_SUPERCLASS("SOLV-SEM-009"),
    /** Semantic validation: the class-inheritance graph contains a cycle. */
    SEM_INHERITANCE_CYCLE("SOLV-SEM-010"),
    /** Semantic validation: a method overrides an inherited method without the {@code override} modifier. */
    SEM_ACCIDENTAL_OVERRIDE("SOLV-SEM-011"),
    /** Semantic validation: a method is marked {@code override} but no inherited method matches. */
    SEM_OVERRIDE_WITHOUT_SUPER("SOLV-SEM-012"),
    /** Semantic validation: an override targets a method that is not {@code open}. */
    SEM_OVERRIDE_FINAL("SOLV-SEM-013"),
    /** Semantic validation: an override has incompatible parameter or return types. */
    SEM_OVERRIDE_SIGNATURE("SOLV-SEM-014"),
    /** Semantic validation: {@code super} is used as a value rather than as a call or member receiver. */
    SEM_SUPER_AS_VALUE("SOLV-SEM-016"),
    /** Semantic validation: {@code super(...)} is not the first statement of an {@code init}. */
    SEM_SUPER_CALL_PLACEMENT("SOLV-SEM-017"),
    /** Semantic validation: a subclass initializer must call {@code super(...)} but does not. */
    SEM_MISSING_SUPER_INIT("SOLV-SEM-018"),
    /** Semantic validation: a subclass declares no {@code init} but its superclass requires arguments. */
    SEM_MISSING_SUPER_INIT_IMPLICIT("SOLV-SEM-019"),
    /** Semantic validation: a class does not implement an inherited interface requirement. */
    SEM_MISSING_INTERFACE_IMPLEMENTATION("SOLV-SEM-020"),
    /** Semantic validation: several interface defaults supply one method and the class resolves none. */
    SEM_CONFLICTING_DEFAULTS("SOLV-SEM-021"),
    /** Semantic validation: an {@code implements} or interface {@code extends} clause names a non-interface. */
    SEM_INVALID_INTERFACE("SOLV-SEM-022"),
    /** Semantic validation: an implementing method has incompatible parameter or return types. */
    SEM_IMPLEMENTATION_SIGNATURE("SOLV-SEM-023"),
    /** Semantic validation: the interface-extension graph contains a cycle. */
    SEM_INTERFACE_CYCLE("SOLV-SEM-024"),
    /** Semantic validation: a {@code delegate} property does not have an interface type. */
    SEM_INVALID_DELEGATE_TYPE("SOLV-SEM-025"),
    /** Semantic validation: two delegates supply one interface member with no explicit resolution. */
    SEM_AMBIGUOUS_DELEGATION("SOLV-SEM-026"),
    /** Semantic validation: a member forwarded by a delegate does not conform to its requirement. */
    SEM_DELEGATE_SIGNATURE("SOLV-SEM-027"),
    /** Semantic validation: a sealed (abstract) class is constructed directly. */
    SEM_CANNOT_CONSTRUCT_SEALED("SOLV-SEM-028"),
    /** Semantic validation: a {@code match} does not cover every known variant or permits null. */
    SEM_MATCH_NOT_EXHAUSTIVE("SOLV-SEM-029"),
    /** Semantic validation: a {@code match} branch can never be selected. */
    SEM_MATCH_UNREACHABLE_PATTERN("SOLV-SEM-030"),
    /** Semantic validation: a {@code break} directly inside a {@code switch} case would exit the switch. */
    SEM_BREAK_IN_SWITCH_CASE("SOLV-SEM-031"),
    /** Semantic validation: a {@code switch} case label is not a compile-time constant. */
    SEM_SWITCH_CASE_NOT_CONSTANT("SOLV-SEM-032"),
    /** Semantic validation: a {@code switch} declares more than one {@code default}. */
    SEM_SWITCH_DUPLICATE_DEFAULT("SOLV-SEM-033"),
    /** Semantic validation: a {@code switch} {@code default} is not the last case. */
    SEM_SWITCH_DEFAULT_NOT_LAST("SOLV-SEM-034");

    private final String stableCode;

    DiagnosticCode(String stableCode) {
        this.stableCode = stableCode;
    }

    /** The stable external spelling of this code. */
    public String stableCode() {
        return stableCode;
    }
}
