// Solvik TCK SOL-TCK-0309
// The unary `?` yields the `Integer` payload 41, so `+ 1` produces 42; the payload is used as a value of its declared type `T`, not merely rendered.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The postfix operator `expression?` is the propagation form for `Result` values. Its operand must have a `Result<T, E>` type; the value of the expression is the unwrapped success payload of type `T`,
//   - The operand is evaluated exactly once.
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
    var v: Integer = get(ok)? + 1
    return Result.Ok(v)
}
var r: Result<Integer, String> = use(true)
print("ok=" .. r.isOk() .. " v=" .. r.unwrap())
