// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 3,
// verbatim: "val freezes the binding, not the complete reachable object graph", with the
// accompanying example whose `user.name = "Douglas"` is "valid" and whose
// `user = User("Other")` is a "compile error". Section 3 also states "Reassignment is
// illegal" for `val`, shown as `count = 2 // compile error`.
//
// Deliberate scope limit: the specification requires the rejection but names NO stable
// diagnostic code for reassigning an immutable binding (section 3 says only "compile
// error"). TCK.md section 6 forbids treating an implementation enum entry as normative,
// so this manifest asserts only the family, which IS the protocol's closed taxonomy.
// The category is a specification-level fact (section 3: "Assignments are statements";
// the error is static, raised before execution), so the family is TYPE.
//
// Sentinel per TCK.md section 10: the println would be observable if this invalid
// program were executed; the compile-only phase independently proves it was not.
val count: Integer = 1
count = 2
println("EXECUTED-INVALID")
