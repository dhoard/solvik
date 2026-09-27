// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.8, which
// gives this exact program as its invalid example: "a function body does not implicitly
// return its final expression", followed by
//   func invalid(): Integer {
//       42 // compile error: a value-returning function requires return 42
//   }
// Section 21's preamble adds that a construct with an error "never produces an executable
// call target". The code for the violated rule is named in section 17: a value-returning
// function "must return on every path (`SOLV-TYPE-012`)". The implementation also reports a
// second diagnostic here whose code appears nowhere in the specification, and this
// expectation deliberately pins only the code the specification names.
func invalid(): Integer {
    42
}
print("EXECUTED-INVALID")
