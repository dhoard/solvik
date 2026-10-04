// Solvik TCK SOL-TCK-0432
// Two branches with unrelated parameters have no function-type join, so the join selects `Any` and cannot manufacture a callable function type; invoking it is the pinned code.
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

class Unrelated {
    val tag: String = "u"
}

func fromUnrelated(value: Unrelated): String {
    return value.tag
}

// The two branch types have unrelated parameters, so no function-type join exists and the
// ordinary join selects a shared nominal supertype instead. A join never introduces
// `Nothing`, a union, or an intersection to manufacture a function supertype, so the joined
// value is not of a function type and invoking it is an invocation whose callee is not a
// function type -- the code the section names verbatim for that shape.
val flag: Boolean = true
val joined = if (flag) {
    format
}
else {
    fromUnrelated
}
print(joined(1))
print("EXECUTED-INVALID")
