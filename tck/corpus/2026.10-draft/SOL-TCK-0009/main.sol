// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section "Static
// members and class initialization" (section 7): verbatim, "A static member is **not
// inherited** and is **not overridable**. ... Declaring `open` or `override` on a static
// member is `SOLV-SEM-047`." The same code is listed in that section's diagnostic table
// as SEM_INVALID_STATIC_MODIFIER = SOLV-SEM-047, "a static member declared `open` or
// `override`". The program below declares a static method with the `open` modifier, the
// exact condition the rule names; the code is a specification derivation, not a value
// captured from the implementation.
// The final println is the sentinel required by TCK.md section 10: if this invalid
// program were ever executed, observable stdout would appear; the compile-only phase
// independently proves execution did not occur.
class Widget {
    static open func build(): Integer {
        return 1
    }
}

println("EXECUTED-INVALID")
