// Solvik TCK SOL-TCK-0447
// The same form rule at two other types: a closure returning String and a Unit closure discarding its argument, so neither the result type nor the Unit default rests on one shape
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function is an expression written with `func`, an optional capture list, a parenthesized parameter list, an optional return type, and a body:
//   - Its parameters must have explicit types.
//   - Its return type follows the same rule as a named function: omitting it declares `Unit`; a value-returning anonymous function must write its return type and must return a compatible value on every normally completing path.
//   - Function bodies never acquire an implicit tail result, and the ordinary return diagnostics apply inside an anonymous function exactly as they do in a declaration.
//   - A bare anonymous function or function reference used as an expression statement remains invalid, because creating and discarding a function value is not a call.
//
var formatter: func(Integer): String = func(value: Integer): String {
    return "v" .. value.toString()
}

var consume: func(String) = func(value: String) {
    print(value)
}

print(formatter(3) .. "|")
consume("unit")
