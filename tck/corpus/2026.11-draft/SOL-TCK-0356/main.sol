// Solvik TCK SOL-TCK-0356
// The trailing comma contributes no third argument, so the call has source-level arity 2.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A call's argument list may end with a trailing comma (`add(1, 2,)`). The trailing comma contributes no argument, so it never affects arity. The list still requires at least one argument, so `add(,)` is a parse error while `add()` is the ordinary empty argument list.
//
func add(a: Integer, b: Integer): Integer {
    return a + b
}
print("tc" .. add(1, 2,))
