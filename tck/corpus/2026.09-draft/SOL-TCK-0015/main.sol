// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 23.2,
// verbatim: "A `Result`-typed value used as a standalone statement (a call expression
// whose result type is a `Result`) is the compile-time error SEM_UNUSED_RESULT
// (SOLV-SEM-052)" and "A standalone `Result` call such as `compute()` therefore
// requires one of these consumptions; `compute().ignore()` is accepted and `compute()`
// alone is rejected." The program below is exactly the rejected shape named by the
// spec. The code is named verbatim in section 23.2, so the oracle is a specification
// derivation, not a value captured from the implementation.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
enum Result<T, E> {
    Ok(T)
    Err(E)
}

func compute(): Result<Integer, String> {
    return Result.Ok(1)
}

compute()
println("EXECUTED-INVALID")
