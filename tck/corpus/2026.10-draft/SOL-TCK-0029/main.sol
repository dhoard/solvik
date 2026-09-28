// Positive equality conformance test: `==` widens mixed numeric operands before
// comparing. Oracle derived by hand from LANGUAGE_SPEC section 3, verbatim: "`==`/`!=`
// between two numeric operands of different types first widen both to their least common
// widened numeric type (section 4), so `1 == 1L` compares as `Long` and `1.5f == 1.5`
// compares as `Double`." The specification gives both asserted cases as its own examples,
// so the expected results are quoted derivations: each comparison is true.
// `1L` is a section 1 Phase 7 `L`-suffixed Long literal; `1.5f` is an `F`/`f`-suffixed
// Float literal per section 1 ("an `F` suffix selects `Float`").
// Expected bytes: `true|true`.
val one: Integer = 1
val oneLong: Long = 1L
print(one == oneLong)
print("|")
val f: Float = 1.5f
val d: Double = 1.5
print(f == d)
