// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5 via
// the section 21.9 required-diagnostic table: SEM_SWITCH_EXPRESSION_MISSING_DEFAULT =
// SOLV-SEM-043, reported on the "whole `switch` expression". Section 21.9 states that
// "existing type errors inside a tail expression keep their existing codes", and
// SOLV-TYPE-038 is reserved for "normally completing branches with no single nearest
// common declared supertype", so a `switch` expression lacking a default arm is
// SOLV-SEM-043 rather than a fabricated branch-type mismatch.
//
// Arm syntax follows section 21.5 exactly (`case <label>:` / `default:` arms, each body
// ending in a tail expression); the default arm is the one whose omission is required.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
var mode: Integer = 1
var label: String = switch (mode) {
    case 1 {
        "one"
    }

    case 2 {
        "two"
    }
}

println("EXECUTED-INVALID")
