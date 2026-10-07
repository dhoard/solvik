// Solvik TCK SOL-TCK-0399
// expect on an Ok returns the success payload and ignores the message.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `expect(message)` returns the success payload of an `Ok`, ignoring the message.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(5)
}
var r: Result<Integer, String> = get()
print("exp" .. r.expect("msg"))
