// Runtime-fault conformance test. Oracle derived by hand from LANGUAGE_SPEC section 23
// operation table ("`unwrapErr` ... the error payload; faults on the success variant")
// and section 23.1, which names `unwrapErr` on an `Ok` as raising a Solvik runtime fault
// of the same class as an arithmetic fault — an ordinary guest failure, detected at run
// time at the operation. Category as in SOL-TCK-0013: the protocol's normative
// RESULT_WRONG_VARIANT classification (protocol.md section 4.1), not captured from the IUT.
// Location not asserted for the section 6.1 reason recorded in SOL-TCK-0013. No output
// precedes the fault, so it happens with empty stdout.
enum Result<T, E> {
    Ok(T)
    Err(E)
}

func good(): Result<Integer, String> {
    return Result.Ok(42)
}

var e: String = good().unwrapErr()
