// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Prefixes are file-local and non-transitive: a file does not inherit the prefixes or
//    aliases of the files it includes; it must include a file itself to reference it."
// main.sol includes lib/inner.sol but not lib/deep.sol. inner.sol includes deep.sol,
// whose module declaration would make `deepmod` visible in *inner.sol*; the quoted
// sentence says that visibility does not propagate outward, so the qualified reference
// from main.sol must be rejected.
// The include inside inner.sol is written "deep.sol", relative to inner.sol's own
// directory: section 20 expands each include from its own file, and writing
// "lib/deep.sol" there would name a nonexistent lib/lib/deep.sol and produce a not-found
// rejection for a completely different reason than the rule under test.
// Assertion strength: RESOL family only. The sentence requires rejection but names no
// code, and section 20 does not say which diagnostic covers a prefix that was never made
// visible in this file. SOL-TCK-0092 is the positive control: the same module reference
// succeeds when main.sol includes deep.sol's module file itself, so the rejection here is
// attributable to non-transitivity and not to the declaration or the separator.
// Sentinel per TCK.md section 10: the print would be observable if the prefix leaked.
include "lib/inner.sol"

print(deepmod::get())
