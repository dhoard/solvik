// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.3,
// verbatim: "Explicit and synthesized semicolons are the same parser token and have the
// same language meaning, so token origin is never inspected to decide whether a value
// exists. All three forms below have the same value and type" -- and the spec lists
// `val a = { 42 }`, the multi-line form, and `val c = { 42; }`, each "an `Integer` block
// expression with value 42". The section adds "Comments and blank lines before `}` do not
// affect tail selection", which the fourth form here exercises.
// Four spellings of the same value 42, so the expected stdout is "42424242". Any spelling
// that lost the tail expression would instead be a compile-time error.
val a = { 42 }
val b = {
    42
}
val c = {
    42;
}
val d = {
    42

    // a comment and blank lines must not disturb tail selection

}
print(a)
print(b)
print(c)
print(d)
