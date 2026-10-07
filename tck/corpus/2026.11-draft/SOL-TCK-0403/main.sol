// Solvik TCK SOL-TCK-0403
// isOk and isErr report opposite verdicts on the same value without faulting.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `isOk` and `isErr` are complementary tests over the variant. They never fault.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(1)
}
var r: Result<Integer, String> = get()
print(r.isOk())
print(r.isErr())
