// Solvik TCK SOL-TCK-0319
// A `catch (e: Exception)` around the propagation must not intercept a return; if `?` were a guest throw the handler would print `CAUGHT`, but the expected bytes are `err=true e=x`.
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
        var v = get()?
        return Result.Ok(v)
    }
    catch (e: Exception) {
        print("CAUGHT")
        return Result.Ok(0)
    }
}
var r = use()
print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())
