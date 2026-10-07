// Solvik TCK SOL-TCK-0401
// The message expression prints its marker once before the Ok payload, so it was evaluated exactly once.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The message must be assignable to `String` and is evaluated exactly once whenever the call runs, on either variant.
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func msg(): String {
    print("m")
    return "x"
}
func get(): Result<Integer, String> {
    return Result.Ok(1)
}
var r: Result<Integer, String> = get()
print(r.expect(msg()))
