// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "Inside a method or constructor a bare name never resolves to a property: it denotes a
//    local, a parameter, a function, or a top-level declaration, and if none is visible the
//    reference is `SOLV-RESOL-001`."
// Obligation: a bare `size` inside a method must NOT resolve to the property of the same
// name, and with no other visible `size` the reference is the specification-named stable
// code SOLV-RESOL-001. Because section 7 names that code for exactly this rule, the
// manifest pins the full code rather than only the diagnostic family.
class Box {
    var size: Integer

    Box(size: Integer) {
        this.size = size
    }

    func report(): String {
        return "size=" .. size
    }
}

print(Box(3).report())
