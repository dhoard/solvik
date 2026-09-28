// Solvik TCK SOL-TCK-0400
// expect on an Err faults at run time with the wrong-variant category.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - On an `Err` it raises a runtime fault reporting `message` together with the carried error (section 23.1).
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Err("boom")
}
val r = get()
print(r.expect("custom"))
