// expected: SOLV-PARS-013
// A local declaration writes its type; the retired inferred form is rejected at the declaration
// (docs/LANGUAGE_SPEC.md section 6).
func demo() {
    var inferred = 1
}
