// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 22.2:
// "The operand type must be assignable to a guest exception type (an exception type
// per section 22.1); throwing any other value is the compile-time error SOLV-SEM-053
// (SEM_THROW_NON_EXCEPTION), reported on the operand." Section 22.1 defines a guest
// exception type as a class whose superclass chain reaches Exception, RuntimeException,
// or ApplicationException; `class NotAnError { }` reaches none of them, so throwing an
// instance of it is SOLV-SEM-053. The code is named verbatim in section 22.2's table
// and section 22.6, not captured from the implementation.
// The final println is the sentinel required by TCK.md section 10: if this invalid
// program were ever executed, observable stdout would appear. The compile-only phase
// independently proves execution did not occur.
class NotAnError {

}

throw NotAnError()

println("EXECUTED-INVALID")
