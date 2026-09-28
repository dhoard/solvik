// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "a named module merges the declarations of every file that declares that module and
//    rejects a duplicate within it", and, one sentence earlier, fixes the code:
//    "a duplicate name is `SOLV-RESOL-002`".
// The bullet list states the merge rule directly: "Two files that declare the same module
// name are one module and their declarations merge; a duplicate declaration within the
// merged module is `SOLV-RESOL-002`."
// Both files declare module `shared` and both declare `func add` with the same name, so
// the merged module holds a duplicate declaration and the sentence names
// `SOLV-RESOL-002` for exactly that condition; the manifest pins it.
// Sentinel per TCK.md section 10: the print would be observable if the duplicate were
// accepted.
include "lib/m.sol"
include "lib/n.sol"

print(shared::add(2, 3))
