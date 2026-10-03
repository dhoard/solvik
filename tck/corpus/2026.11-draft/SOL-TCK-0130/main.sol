// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "Every normally completing branch must produce a tail result, and abrupt
// branches are excluded from result joining", illustrated by the spec's own
//   func requireName(name: String?): String {
//       return if (name != null) { name } else { return "fallback" }
//   }
// The `else` returns from the enclosing function, so it is abrupt and contributes nothing
// to the join; only the non-null branch is the `if` expression's result. Applying the
// function to a non-null argument yields "z" and to null yields "fallback", bracketed here
// so both appear in one stream. Expected stdout is "[z][fallback]".
func requireName(name: String?): String {
    return if (name != null) {
        name
    } else {
        return "fallback"
    }
}
print("[")
print(requireName("z"))
print("]")
print("[")
print(requireName(null))
print("]")
