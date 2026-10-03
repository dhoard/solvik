// Solvik TCK SOL-TCK-0453
// A capture item is an identifier or `this`, so an arbitrary capture expression is refused at the list itself
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every such dependency must appear in an explicit capture list between `func` and the parameter list:
//   - The capture list is part of the anonymous-function expression but not part of its function type: the example above has type `func(Integer): Integer`, because callers supply `value` while the declaration visibly binds `factor` into the function value.
//   - An empty capture list is a parse error, because a non-capturing anonymous function is written `func(...)`.
//   - Capture aliases and arbitrary capture expressions are not supported.
//
func run(base: Integer): func(Integer): Integer {
    return func [base + 1](value: Integer): Integer {
        return value
    }
}

print("EXECUTED-INVALID")
