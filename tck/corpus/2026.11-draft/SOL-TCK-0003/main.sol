// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 3, "The
// equals/hashCode pairing rule" (a class that declares `override func equals` must also
// declare `override func hashCode`) and section 21.9, which names the stable code
// SEM_EQUALS_WITHOUT_HASHCODE = SOLV-SEM-045 (equals declared without hashCode; the
// program below declares equals but omits hashCode, so the expected code is 045).
// Override syntax mirrors the spec-validated HashCode example. The final println is a
// sentinel: if the invalid program were ever executed, observable stdout would appear;
// the compile-only phase independently proves execution did not occur.
class Point {
    var x: Integer

    Point(x: Integer) {
        this.x = x
    }

    method override equals(other: Any?): Boolean {
        if (other is Point) {
            return this.x == other.x
        }
        return false
    }
}

println("EXECUTED-INVALID")
