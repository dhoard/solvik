// Solvik TCK SOL-TCK-0431
// A function value is assigned to `Any` and passed to an `Any` parameter, and two branches of identical function type join to a value that is still callable.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every non-null function type has `Any` as its top supertype, and a nullable function type relates to another under those same rules.
//   - The shared type join understands function types: for two same-arity function types each joined parameter takes the more specific of the two when one is assignable to the other, and the joined result is their nearest common result type. That joined function type is the least common function supertype allowed by contravariant parameters and covariant results.
//   - When a parameter pair is unrelated or the results have no unique join, no function-type join exists and the ordinary join may still select a shared nominal supertype such as `Any`; a join never introduces `Nothing`, a union, or an intersection in order to manufacture a function supertype.
//   - Generic type arguments remain invariant, so `List<func(Dog): Animal>` and `List<func(Animal): Dog>` are unrelated applications even though the function types inside them are comparable.
//   - An invocation whose callee is not a function type is `SOLV-TYPE-002`, and a call with the wrong number of arguments is `SOLV-TYPE-003`.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
func format(value: Integer): String {
    return "v" .. value.toString()
}

func describe(value: Integer): String {
    return "d" .. value.toString()
}

func takeAny(value: Any): String {
    return value.toString()
}

// Every non-null function type has `Any` as its top supertype, so a function value is
// assignable to `Any` and may be passed where `Any` is expected.
var asAny: Any = format

// The shared type join understands function types: these two branches have identical
// parameter types and identical result types, so the join is that function type and the
// joined value stays callable.
var flag: Boolean = true
var joined = if (flag) {
    format
}
else {
    describe
}

print(takeAny(asAny) .. "|" .. takeAny(joined) .. "|" .. joined(2))
