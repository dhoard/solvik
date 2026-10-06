// Runtime-fault conformance test. Oracle derived by hand from LANGUAGE_SPEC section 23
// operation table ("`unwrap` ... the success payload; faults on the error variant") and
// section 23.1, verbatim: "`unwrap` on an `Err`, `unwrapErr` on an `Ok`, and `expect` on
// an `Err` raise a Solvik runtime fault of the same class as an arithmetic, cast, or
// bounds fault (an ordinary guest failure reported to the host with a non-zero exit
// status, not an internal error)." Section 23.1 adds that the fault "can only occur on a
// receiver whose static type is a `Result`, so it is detected at run time (at the
// operation)", so the outcome is RUNTIME_ERROR, not COMPILE_ERROR. The expected
// structured category is the protocol's normative classification of a Result
// wrong-variant fault (protocol.md section 4.1: RESULT_WRONG_VARIANT), a spec+protocol
// derivation, not a value captured from the IUT. Section 23.1's "source location of the
// faulting operation" is not asserted: the spec names the location obligation but does
// not define the exact span, and TCK.md section 6.1 forbids the TCK choosing an
// observable the spec leaves open. No output precedes the fault, so it happens with
// empty stdout. The `Result` enum declaration follows section 12's two-parameter shape.
enum Result<T, E> {
    Ok(T)
    Err(E)
}

func bad(): Result<Integer, String> {
    return Result.Err("boom")
}

var n: Integer = bad().unwrap()
