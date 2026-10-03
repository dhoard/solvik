// Solvik TCK SOL-TCK-0446
// A value-returning anonymous function writes its return type, a Unit one omits it, and a closure written in a call argument keeps an explicit semicolon because insertion is suppressed while a parenthesis is unmatched
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function is an expression written with `func`, an optional capture list, a parenthesized parameter list, an optional return type, and a body:
//   - Its parameters must have explicit types.
//   - Its return type follows the same rule as a named function: omitting it declares `Unit`; a value-returning anonymous function must write its return type and must return a compatible value on every normally completing path.
//   - Function bodies never acquire an implicit tail result, and the ordinary return diagnostics apply inside an anonymous function exactly as they do in a declaration.
//   - A bare anonymous function or function reference used as an expression statement remains invalid, because creating and discarding a function value is not a call.
//
func invoke(f: func(Integer): Integer): Integer {
    return f(2)
}

val double: func(Integer): Integer = func(value: Integer): Integer {
    return value * 2
}

val report: func(Integer): Unit = func(value: Integer) {
    print("[" .. value.toString() .. "]")
}

val nested: Integer = invoke(func(value: Integer): Integer {
    return value - 100;
})

print(invoke(double).toString() .. "\n")
report(7)
print("\n")
print(nested.toString())
