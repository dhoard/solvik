// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "`this.name` resolves against the enclosing class's properties including inherited
//    ones, and a local may shadow a property name without either reference becoming
//    ambiguous."
// Obligation: with a local declared over a property of the same name, `this.size` must
// still select the property and the bare name must select the local. Printing both in one
// stream makes any ambiguity or mis-resolution produce a different two-value string; the
// derived pair is the constructor argument 3 and the local 99. This is the positive twin
// of the SOLV-RESOL-001 case: together they pin that the bare/qualified distinction is
// real rather than an implementation accident.
class Box {
    val size: Integer

    Box(size: Integer) {
        this.size = size
    }

    func report(): String {
        val size: Integer = 99
        return "shadow=" .. this.size .. "/" .. size
    }
}

print(Box(3).report())
