// Solvik TCK SOL-TCK-0359
// The value-less function's result is bound to a Unit local and its fixed rendering is `Unit`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Built-in scalars provide fixed, non-overridable implementations: `Integer`, `Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use Java-style floating-point text, `Boolean` renders `true` or `false`, `Character` renders its character, `String` renders its contents, and `Unit` renders `Unit`.
//   - `Unit` has one value and is the result of a function that returns normally without a value.
//
func f() {
    print("x")
}
var u: Unit = f()
print(u)
