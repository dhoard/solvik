// Oracle derived by hand from LANGUAGE_SPEC section 6, verbatim:
//   "Declaration lookup remains order-independent within a module, so a declaration may
//    be referenced from a physically earlier file or statement."
//   "The top-level statements, in include-expansion order, form the body of an implicit
//    `func main()`"
// Obligation: a top-level statement calls a function declared textually *after* it. The
// expected bytes "got=42" are computed from the declaration (2 * 21), not observed.
var n: Integer = later(2)
print("got=" .. n)

func later(x: Integer): Integer {
    return x * 21
}
