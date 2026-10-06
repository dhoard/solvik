// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 3's
// equals/hashCode pairing rule, verbatim: "A class that declares `override func equals`
// must also declare `override func hashCode` in the same class declaration, and a class
// that declares `override func hashCode` must also declare `override func equals`."
// SOL-TCK-0003 already covers the first half; this is the second half. Section 21.9 names
// the stable code SEM_HASHCODE_WITHOUT_EQUALS = SOLV-SEM-044, whose primary span is "the
// `hashCode` override declared without `equals`". Override syntax mirrors the
// spec-validated hashCode/equals example used by SOL-TCK-0003.
class OnlyHash {
    var n: Integer

    OnlyHash(n: Integer) {
        this.n = n
    }

    override func hashCode(): Integer {
        return this.n
    }
}
print("EXECUTED-INVALID")
