// Solvik TCK SOL-TCK-0321
// The `Ok` path propagates a value that `isOk`/`unwrap` then read, and the `Err` path propagates an error that `isErr`/`unwrapErr` then read, so the operator and the operations both work on the same values. A `?` defined via `unwrap` would fault on the `Err` arm.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The `?` operator and the operations of section 23 are independent: neither is defined in terms of the other, and a program may use either or both.
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
    var v = get(ok)? + 1
    return Result.Ok(v)
}
var a = use(true)
print("a=" .. a.isOk() .. "," .. a.unwrap())
var b = use(false)
print(" b=" .. b.isErr() .. "," .. b.unwrapErr())
