// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Within the implicit default module all top-level functions, classes, interfaces, and
//    enums share one declaration scope, and a duplicate name is `SOLV-RESOL-002`".
// Neither included file declares a module, so both `add` declarations belong to the
// implicit default module and share the one declaration scope the sentence names; the
// second is therefore a duplicate within that scope and the sentence names
// `SOLV-RESOL-002`. SOL-TCK-0096 covers the named-module half of the sentence.
// Sentinel per TCK.md section 10: the print would be observable if the duplicate were
// accepted.
include "lib/m.sol"
include "lib/n.sol"

print(add(2, 3))
