// Solvik TCK SOL-TCK-0313
// The statement after the `?` is not evaluated once the `Err` returns; the output is exactly `err=true`, so an implementation that continued the body would emit an extra `AFTER`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - On `Ok(value)` the operand's success payload becomes the value of `expression?`. On `Err(error)` the current function returns `Err(error)` immediately, without evaluating the rest of its body.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Err("x")
}
func use(): Result<Integer, String> {
    var v: Integer = get()?
    print("AFTER")
    return Result.Ok(v)
}
var r: Result<Integer, String> = use()
print("err=" .. r.isErr())
