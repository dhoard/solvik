// Solvik TCK SOL-TCK-0310
// `probe` prints `p` once, so the operand was evaluated exactly once on the success path.
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
    return Result.Ok(7)
}
func use(): Result<Integer, String> {
    val v = probe()?
    return Result.Ok(v)
}
val r = use()
print("v=" .. r.unwrap())
