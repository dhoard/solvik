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
    /**
     * The source nests constructs more deeply than the compiler can process. Right-recursive rules
     * (a parenthesized expression, a call chain, a variant pattern, a type-argument list) consume
     * compiler stack per nesting level, so the limit is a compiler resource limit rather than a
     * language rule. Reported as an error so an over-deep file is rejected with a diagnostic instead
     * of failing with a VM resource exhaustion.
     */
    PARSER_NESTING_TOO_DEEP("SOLV-PARS-005"),

    /** Name resolution: no declaration is visible for the referenced name. */
    RESOL_UNKNOWN_NAME("SOLV-RESOL-001"),
    /** Name resolution: the same name is declared twice in one scope. */
    RESOL_DUPLICATE_NAME("SOLV-RESOL-002"),
    /** Name resolution: a written type name does not denote a known Solvik type. */
    RESOL_UNKNOWN_TYPE("SOLV-RESOL-003"),
    /** Name resolution: a member access names a property or method the receiver type does not declare. */
    RESOL_UNKNOWN_MEMBER("SOLV-RESOL-004"),
    /** Name resolution: {@code this} is used outside an instance method or constructor. */
    RESOL_THIS_OUTSIDE_CLASS("SOLV-RESOL-005"),

    /** Static typing: a value is not assignable to the required type. */
    TYPE_MISMATCH("SOLV-TYPE-001"),
    /** Static typing: a call target is not a declared function. */
    TYPE_NOT_CALLABLE("SOLV-TYPE-002"),
    /** Static typing: a statically resolved call supplies a number of arguments other than the callable's parameter count. */
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
    /** Static typing: an integer literal is outside the signed 32-bit {@code Integer} range. */
    TYPE_INTEGER_LITERAL_OUT_OF_RANGE("SOLV-TYPE-013"),
    /** Static typing: a function name is used where a value is required. */
    TYPE_FUNCTION_AS_VALUE("SOLV-TYPE-014"),
    /** Static typing: a class name is used where a value is required. */
    TYPE_CLASS_AS_VALUE("SOLV-TYPE-016"),
    /** Static typing: a property is read before it is definitely initialized. */
    TYPE_UNINITIALIZED_PROPERTY("SOLV-TYPE-017"),
    /** Static typing: a property without a declaration initializer is not assigned on every path. */
    TYPE_MISSING_PROPERTY_INITIALIZER("SOLV-TYPE-018"),
    /** Static typing: a {@code Long} literal is outside the signed 64-bit {@code Long} range. */
    TYPE_LONG_LITERAL_OUT_OF_RANGE("SOLV-TYPE-019"),
    /** Static typing: a character literal does not contain exactly one character or a known escape. */
    TYPE_INVALID_CHARACTER_LITERAL("SOLV-TYPE-020"),
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
    /**
     * Static typing: the normally completing branches of a value-producing {@code if} or
     * {@code switch} expression have no single nearest common declared supertype.
     */
    TYPE_BRANCH_RESULT("SOLV-TYPE-038"),
    /**
     * Static typing: an identity operand lacks the specified Solvik allocation identity. Applied to
     * the complete {@code ===}/{@code !==} expression even when its two operand types are
     * assignment-compatible, because {@code Any} and unbounded type parameters are compatible but
     * never identity-bearing (docs/LANGUAGE_SPEC.md section 3).
     */
    TYPE_IDENTITY_OPERANDS("SOLV-TYPE-039"),

    /** Name resolution: {@code super} is used outside a class or where no superclass exists. */
    RESOL_SUPER_OUTSIDE_CLASS("SOLV-RESOL-006"),

    /** File inclusion: the written include path is empty, lacks the {@code .sol} extension, or has no home directory to expand. */
    RESOL_INCLUDE_INVALID_PATH("SOLV-RESOL-007"),
    /** File inclusion: the target file does not exist. */
    RESOL_INCLUDE_NOT_FOUND("SOLV-RESOL-008"),
    /** File inclusion: the target path exists but is not a regular file. */
    RESOL_INCLUDE_NOT_FILE("SOLV-RESOL-009"),
    /** File inclusion: reading the target file was denied or failed with an I/O error. */
    RESOL_INCLUDE_IO("SOLV-RESOL-010"),
    /** File inclusion: the include graph contains a cycle. */
    RESOL_INCLUDE_CYCLE("SOLV-RESOL-011"),

    /** Modules: a {@code module} or {@code alias} name does not match the required lowercase, underscore-separated form. */
    RESOL_MODULE_INVALID_NAME("SOLV-RESOL-012"),
    /** Modules: a file binds the same prefix twice, or two visible prefixes collide. */
    RESOL_ALIAS_DUPLICATE("SOLV-RESOL-013"),
    /** Modules: {@code alias} names a file that belongs to the implicit default module, which has no name to bind. */
    RESOL_ALIAS_DEFAULT_MODULE("SOLV-RESOL-014"),
    /** Modules: a qualified reference names a module prefix that is not visible in this file. */
    RESOL_UNKNOWN_MODULE("SOLV-RESOL-015"),

    /** Semantic validation: an explicit {@code main} is declared although the entry point is implicit. */
    SEM_INVALID_ENTRY_POINT("SOLV-SEM-001"),
    /** Semantic validation: {@code break} or {@code continue} appears outside a loop. */
    SEM_LOOP_CONTROL_OUTSIDE_LOOP("SOLV-SEM-002"),
    /** Semantic validation: a value-producing expression is used as a statement and is not a call. */
    SEM_VALUE_EXPRESSION_STATEMENT("SOLV-SEM-003"),
    /** Semantic validation: a {@code for} initializer is neither a local declaration nor an assignment. */
    SEM_FOR_INITIALIZER("SOLV-SEM-004"),
    /** Semantic validation: a {@code for} update clause is not an assignment. */
    SEM_FOR_UPDATE("SOLV-SEM-005"),
    /** Semantic validation: a class without a constructor has a property without an initializer. */
    SEM_CLASS_REQUIRES_INITIALIZER("SOLV-SEM-006"),
    /** Semantic validation: a class declares more than one constructor. */
    SEM_DUPLICATE_CONSTRUCTOR("SOLV-SEM-007"),
    /** Semantic validation: a class extends a class that was not declared {@code open}. */
    SEM_EXTEND_FINAL("SOLV-SEM-008"),
    /** Semantic validation: an {@code extends} clause names something other than a class or {@code Any}. */
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
    /** Semantic validation: {@code super(...)} is not the first statement of a constructor. */
    SEM_SUPER_CALL_PLACEMENT("SOLV-SEM-017"),
    /** Semantic validation: a subclass constructor must call {@code super(...)} but does not. */
    SEM_MISSING_SUPER_INIT("SOLV-SEM-018"),
    /** Semantic validation: a subclass declares no constructor but its superclass requires arguments. */
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
    SEM_SWITCH_DEFAULT_NOT_LAST("SOLV-SEM-034"),
    /** Semantic validation: a constructor declaration is not named after its class. */
    SEM_CONSTRUCTOR_NAME("SOLV-SEM-035"),
    /** Semantic validation: a non-constructor class member has the same name as its class. */
    SEM_MEMBER_NAMED_AFTER_CLASS("SOLV-SEM-036"),
    /** Semantic validation: a stored member reuses a reserved built-in member name such as {@code toString}. */
    SEM_RESERVED_MEMBER("SOLV-SEM-037"),
    /** Semantic validation: a range {@code for}-in bound is not an {@code Integer}. */
    SEM_INVALID_RANGE_BOUND("SOLV-SEM-038"),
    /** Semantic validation: a sealed class is extended from a physical source file other than its own. */
    SEM_SEALED_SUBTYPE_OUTSIDE_FILE("SOLV-SEM-039"),
    /** Semantic validation: a {@code key: value} entry appears outside a built-in {@code Map} construction. */
    SEM_MAP_ENTRY("SOLV-SEM-040"),
    /**
     * Semantic validation: a value-required block or {@code switch} case body can complete normally
     * without evaluating a tail expression.
     */
    SEM_BLOCK_RESULT_REQUIRED("SOLV-SEM-041"),
    /** Semantic validation: an expression {@code if} has no {@code else} path. */
    SEM_IF_EXPRESSION_MISSING_ELSE("SOLV-SEM-042"),
    /** Semantic validation: an expression {@code switch} has no {@code default} case. */
    SEM_SWITCH_EXPRESSION_MISSING_DEFAULT("SOLV-SEM-043"),
    /** A class declares {@code override hashCode} without also declaring {@code override equals}. */
    SEM_HASHCODE_WITHOUT_EQUALS("SOLV-SEM-044"),
    /** A class declares {@code override equals} without also declaring {@code override hashCode}. */
    SEM_EQUALS_WITHOUT_HASHCODE("SOLV-SEM-045");

    private final String stableCode;

    DiagnosticCode(String stableCode) {
        this.stableCode = stableCode;
    }

    /** The stable external spelling of this code. */
    public String stableCode() {
        return stableCode;
    }
}
