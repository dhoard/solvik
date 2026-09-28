// Oracle derived from LANGUAGE_SPEC section 20's required-diagnostics registry, which
// lists verbatim:
//   "| `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |"
// and whose prose adds: "Messages for path failures include the written path and, when
// one exists, the resolved candidate."
// `lib/nope.sol` is not staged by this test, so the include names a file that does not
// exist: the registry entry whose primary span is the include directive and whose name is
// RESOL_INCLUDE_NOT_FOUND is the diagnostic the registry requires, and the manifest pins
// its stable code. The primary span is deliberately not asserted: the schema's location
// fields pin byte offsets, and while the registry gives the span as "include directive",
// it does not pin its exact boundaries.
// Sentinel per TCK.md section 10: the print would be observable if the missing file were
// somehow tolerated.
include "lib/nope.sol"

print(1)
