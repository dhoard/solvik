// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Binding one prefix twice in a file, including a collision with a prefix an unaliased
//    include already made visible, is `SOLV-RESOL-013`."
// Both directives bind the prefix `m`: the first by explicit alias, the second by the
// same explicit alias on a different file. The clause names both the condition and the
// exact code, so the manifest pins `SOLV-RESOL-013`.
// Note the two included files declare the same module and identical declarations; that
// is deliberate and does not compete with this rule, because section 20 binds prefixes
// at the directive ("Prefixes are file-local") and prefix binding is what the quoted
// sentence rejects, before any cross-file declaration merge is consulted.
// Sentinel per TCK.md section 10: the print would be observable if this invalid double
// binding were accepted.
include "lib/m.sol" alias m
include "lib/n.sol" alias m

print(m::add(2, 3))
