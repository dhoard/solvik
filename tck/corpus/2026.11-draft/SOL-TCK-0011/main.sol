// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "An expression `if` must have an `else`; a missing `else` is a dedicated
// compile-time error and does not also fabricate a branch-type mismatch." Section 21.9's
// required-diagnostic table names that dedicated code:
// SEM_IF_EXPRESSION_MISSING_ELSE = SOLV-SEM-042, "whole `if` expression".
//
// The "does not also fabricate a branch-type mismatch" clause is part of the oracle: the
// expected diagnostic is SOLV-SEM-042 specifically, not SOLV-TYPE-038 (the branch-result
// code from the same table). Section 21.4 also notes statement-style `if` remains valid
// without `else`, so the trigger here is `if` used in expression position (a `var`
// initializer) with no `else`.
//
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
var flag: Boolean = true
var label: String = if (flag) {
    "yes"
}
println("EXECUTED-INVALID")
