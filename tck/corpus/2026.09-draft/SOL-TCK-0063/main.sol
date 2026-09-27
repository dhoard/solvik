// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "`this` outside an instance method or constructor is `SOLV-RESOL-005`."
// Obligation: `this` in a top-level function, which is neither an instance method nor a
// constructor, is rejected with the specification-named stable code SOLV-RESOL-005. The
// section names the code for the rule, so the manifest pins it exactly.
func notAMethod(): Integer {
    return this.value
}

print("unreachable")
