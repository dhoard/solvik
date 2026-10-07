// Solvik TCK SOL-TCK-0314
// The `Err` returns through `b` and then `c` unchanged, so the return composes across two call frames and the deepest error text is preserved.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - On `Ok(value)` the operand's success payload becomes the value of `expression?`. On `Err(error)` the current function returns `Err(error)` immediately, without evaluating the rest of its body.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func a(): Result<Integer, String> {
    return Result.Err("deep")
}
func b(): Result<Integer, String> {
    var v: Integer = a()?
    return Result.Ok(v + 1)
}
func c(): Result<Integer, String> {
    var v: Integer = b()?
    return Result.Ok(v + 1)
}
var r: Result<Integer, String> = c()
print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())
