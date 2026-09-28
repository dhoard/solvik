// Solvik TCK SOL-TCK-0402
// ignore evaluates the receiver once and yields Unit, which binds to the declared Unit local.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `ignore` evaluates its receiver exactly once, discards the value, and yields `Unit`.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func probe(): Result<Integer, String> {
    print("p")
    return Result.Ok(1)
}
val u: Unit = probe().ignore()
print("done")
