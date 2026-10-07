// expected: SOLV-PARS-014
// Functions are declarations rather than values, so a function type reference is rejected
// (docs/LANGUAGE_SPEC.md section 6).
func demo(operation: func(Integer): Integer): Integer {
    return operation(1)
}
