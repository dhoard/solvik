// Solvik TCK SOL-TCK-0402
// ignore evaluates the receiver once and produces no value, so the call is a statement and the trailing print shows execution continued.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `ignore` evaluates its receiver exactly once, discards the value, and produces no value.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func probe(): Result<Integer, String> {
    print("p")
    return Result.Ok(1)
}
probe().ignore()
print("done")
