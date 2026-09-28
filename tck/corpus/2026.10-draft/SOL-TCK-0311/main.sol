// Solvik TCK SOL-TCK-0311
// The operand runs once and then returns from `use`, so `p` appears once and `AFTER` does not appear; the expected bytes are `perr=true`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The postfix operator `expression?` is the propagation form for `Result` values. Its operand must have a `Result<T, E>` type; the value of the expression is the unwrapped success payload of type `T`,
//   - The operand is evaluated exactly once.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func probe(): Result<Integer, String> {
    print("p")
    return Result.Err("e")
}
func use(): Result<Integer, String> {
    val v = probe()?;
    print("AFTER")
    return Result.Ok(v)
}
val r = use()
print("err=" .. r.isErr())
