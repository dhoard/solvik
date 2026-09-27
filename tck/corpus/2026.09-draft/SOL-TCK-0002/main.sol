// Oracle derived by hand from LANGUAGE_SPEC section 3's operator precedence list, which
// orders the tiers "from lowest to highest" as `..` at item 6 and `+`, `-` at item 7, so
// `..` is the looser operator and `a + b .. c` groups as `(a + b) .. c`. (Paraphrase: the
// list is the normative text; no prose sentence states the grouping in those words.)
// Obligation: `..` precedence over `+`. `(1 + 2) .. "z"` renders as "3z". print adds no
// separator, so the expected stdout byte string is exact (no line-separator dependency).
print(1 + 2 .. "z")
