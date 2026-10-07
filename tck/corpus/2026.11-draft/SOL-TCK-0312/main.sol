// Solvik TCK SOL-TCK-0312
// The `Err` is returned from `use` unchanged; the caller observes the same `bad` error and the process exits 0, so the transition is a return rather than a fault.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - On `Ok(value)` the operand's success payload becomes the value of `expression?`. On `Err(error)` the current function returns `Err(error)` immediately, without evaluating the rest of its body.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(ok: Boolean): Result<Integer, String> {
    if (ok) {
        return Result.Ok(41)
    }
    return Result.Err("bad")
}
func use(ok: Boolean): Result<Integer, String> {
    var v: Integer = get(ok)?
    return Result.Ok(v + 1)
}
var r: Result<Integer, String> = use(false)
print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())
