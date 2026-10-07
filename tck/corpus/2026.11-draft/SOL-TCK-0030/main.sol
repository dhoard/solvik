// Positive nullability conformance test: null coalescing, safe member access, and
// flow-sensitive narrowing on a reference-identity comparison.
// Oracles derived by hand from LANGUAGE_SPEC section 5, verbatim:
//   * "For `left ?? right`, `left` must be nullable; the result is the common type of
//     non-null `left` and `right`" -- with `name` null, the coalescing yields the right
//     operand, so `display` is `Unknown`.
//   * "For `receiver?.member`, the member is evaluated only when the receiver is
//     non-null and the result type is the member type made nullable" -- with `name` null
//     the member is not evaluated and `rendered` is null.
//   * "Flow-sensitive narrowing is required: if (name != null) { print(name) // name is
//     String here }" -- narrowing makes the non-null use legal, so `Doug` is printed.
// Expected bytes: `Unknown|true|Doug`.
var name: String? = null
var display: String = name ?? "Unknown"
print(display)
print("|")
var rendered: String? = name?.toString()
print(rendered == null)
print("|")
var nn: String? = "Doug"
if (nn != null) {
    print(nn)
}
