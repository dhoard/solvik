// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "If a canonical file is encountered while it is still being expanded, report
//    `SOLV-RESOL-011` at the include that closes the cycle."
// main.sol includes itself. Expansion of main.sol is in progress when its include of
// "main.sol" is reached, so the same canonical file is "encountered while it is still
// being expanded": the self-include is the directive that closes the cycle, and the
// sentence names `SOLV-RESOL-011` at it. A two-file cycle (a includes b, b includes a)
// closes the cycle at whichever edge is reached second, which of the two the
// implementation reports is decided by traversal order; the single-file self-include is
// the shape whose closing directive the specification itself fixes, so that is the form
// tested and the exact code the manifest can attribute to a specific directive.
// Sentinel per TCK.md section 10: the print would be observable if the cycle expanded.
include "main.sol"

print(1)
