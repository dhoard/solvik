// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Including a file that declares a module makes that module's name a visible prefix in
//    the including file. `include P alias p` binds the prefix `p` to the included file's
//    module instead."
// "instead" is the whole rule: with an alias present, the module's own name is not bound
// in the including file. `com_example_math::add` is therefore a reference to a module
// name that was never made visible here, and the required-diagnostics registry names
//   "| `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |"
// for exactly that diagnostic.
// Assertion strength: the manifest pins the RESOL family, not the code. The body sentence
// that creates the rule names no code, and while the registry names SOLV-RESOL-015 for a
// "qualified reference" to an unknown module, section 20 never states that the
// aliased-instead case is reported as an unknown *module* rather than, say, an unknown
// name -- both readings satisfy the body sentence. Family-level is the strongest claim
// the specification text supports, and TCK.md section 6 forbids promoting an
// implementation enum entry to normative status.
// Controls in this corpus: SOL-TCK-0092 shows an unaliased include does make the module
// name visible, and SOL-TCK-0093 shows the alias prefix itself resolves; together they
// exclude the alternatives in which this rejection would be caused by a broken module
// system rather than by `instead`. Sentinel per TCK.md section 10: the print would be
// observable if the reference resolved.
include "lib/m.sol"

print(other_math::add(2, 3))
