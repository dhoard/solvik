// Negative LEXICAL conformance test: a physical newline inside a normal string.
// Oracle derived by hand from LANGUAGE_SPEC section 15, verbatim: "Normal strings
// cannot contain an unescaped physical newline." The program breaks a string literal
// across two physical lines, so the specification determines both the rejection and,
// because the constraint is stated in the lexical rules for string tokens, its lexical
// category.
//
// Deliberate scope limit: as with SOL-TCK-0024/0025, the specification names no code for
// this error (no SOLV-LEX-* code exists in LANGUAGE_SPEC.md), so only the diagnostic
// family is asserted. An implementation may also report a follow-on parse diagnostic;
// the oracle requires only that a LEX-family diagnostic is present, which is exactly
// what the specification determines, so an unrelated extra diagnostic cannot cause a
// spurious pass or failure.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
val s = "line
break"
print(s)
println("EXECUTED-INVALID")
