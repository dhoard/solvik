// Oracle derived by hand from LANGUAGE_SPEC section 20, verbatim:
//   "An explicit `func main` in any participating file remains `SOLV-SEM-001`."
// section 6, verbatim:
//   "The entry point is always implicit: declaring a function named `main` explicitly,
//    in the root or in any included file, is a compile-time error."
// Obligation: an explicit `func main` is rejected with the specification-named stable
// code SOLV-SEM-001. Because section 20 names that code explicitly, this test pins the
// full code rather than only the diagnostic family -- the one kind of exact assertion
// the specification authorizes.
func main() {
    print("never an entry point")
}

print("hi")
