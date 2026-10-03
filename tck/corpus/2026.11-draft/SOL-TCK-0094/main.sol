// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "`alias` naming a file in the default module is `SOLV-RESOL-014`, because the default
//    module has no name to bind."
// `lib/m.sol` declares no `module` item, so section 20 places it in "the implicit default
// module", which the same sentence identifies as having no name to bind. The clause names
// both the condition and the exact code, so the manifest pins `SOLV-RESOL-014`.
// Sentinel per TCK.md section 10: the print would be observable if this invalid alias
// were accepted.
include "lib/m.sol" alias m

print(m::add(2, 3))
