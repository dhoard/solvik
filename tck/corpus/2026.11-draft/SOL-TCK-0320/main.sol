// Solvik TCK SOL-TCK-0320
// The `finally` runs on the propagation exit path before the caller observes the `Err`, so `fin` precedes `err=true e=x`; a transition that skipped `finally` would print neither or the wrong order.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Propagation is a control-flow transition, not a wrong-variant fault: an `Err` carried through `?` returns normally as the enclosing function's `Result` value rather than raising a fault. This is the key distinction from `unwrap`, which faults.
//   - The `finally` block runs on every exit path of the `try`, exactly once per entered `try`:
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Err("x")
}
func use(): Result<Integer, String> {
    try {
        val v = get()?
        return Result.Ok(v)
    }
    finally {
        print("fin")
    }
}
val r = use()
print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())
