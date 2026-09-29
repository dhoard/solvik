// expected: SOLV-SEM-058
// An anonymous function body may not reach an enclosing function's local implicitly. That dependency is
// a capture and must appear in the closure's capture list; here `base` is used in the body but listed by
// no enclosing closure, so the use is reported as unlisted (docs/LANGUAGE_SPEC.md section 6, "Explicit
// immutable closure capture"). The reference is written on the body's own read of `base`, which is where
// the specification locates the diagnostic.
func demo(base: Integer): Integer {
    val compute: func(Integer): Integer = func(value: Integer): Integer {
        return value + base
    }
    return compute(1)
}
print(demo(2))
