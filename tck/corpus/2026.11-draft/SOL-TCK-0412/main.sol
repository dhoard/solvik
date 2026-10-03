// Solvik TCK SOL-TCK-0412
// unwrap on an Ok returns the success payload.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `unwrap` returns the success payload of an `Ok`.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(9)
}
print("uw" .. get().unwrap())
