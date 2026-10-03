// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Including a file that declares a module makes that module's name a visible prefix
//    in the including file."
// and, for the separator: "The included declarations are reached through the prefix with
// the `::` namespace separator".
// Expected bytes derived by hand: the included `add(2, 3)` returns 2 + 3, the integral
// sum section 3 defines, so stdout is `5`. The program has no other output.
// Executed as top-level statements (section 20: expanded executable top-level statements
// form the implicit main). Uses print, so no platform line separator enters the oracle.
include "lib/m.sol"

print(com_example_math::add(2, 3))
