// Positive conformance test: explicit semicolons are equivalent to inserted ones.
// Oracle derived by hand from LANGUAGE_SPEC section 16, verbatim: "Programmers may
// explicitly write `;`, but normal style uses newlines." Section 16's opening states
// Solvik uses Go-style lexical semicolon insertion, so a declaration terminated by an
// explicit `;` and one terminated only by its newline introduce the same binding in the
// same scope. The two bindings are then read back, so the expected stdout is their
// values joined by `-`, i.e. the bytes `1-2`. The `..` rendering used for the join is
// separately owned by REQ-0001; here it is only the observation vehicle.
val x = 1;
val y = 2
print(x .. "-" .. y)
