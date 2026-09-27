// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 22.3,
// verbatim: "A `try` with neither a `catch` clause nor a `finally` clause is the
// compile-time error SOLV-SEM-056 (SEM_TRY_NEEDS_HANDLER), reported on the whole
// statement." The condition is quoted exactly, so the trigger here is a bare `try`
// block carrying no catch clause and no finally clause.
//
// Important boundary this test deliberately respects: section 22.3 permits `try` with a
// `finally` and no `catch`, and permits `try` with `catch` clauses and no `finally`.
// Only the absence of BOTH is SOLV-SEM-056, so this program supplies neither. The code
// is named in section 22.3 and in the section 22.6 required-diagnostic table, making it
// a specification derivation rather than a value captured from the implementation.
//
// The final println is the sentinel required by TCK.md section 10: if this invalid
// program were ever executed, observable stdout would appear; the compile-only phase
// independently proves execution did not occur.
class AppError extends RuntimeException {

}

func risky() {
    throw AppError("unhandled")
}

try {
    risky()
}

println("EXECUTED-INVALID")
