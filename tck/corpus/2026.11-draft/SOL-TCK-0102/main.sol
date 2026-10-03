// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "A canonical physical file is expanded at most once per evaluated root. A later
//    include of the same canonical file is a no-op, so a diamond is deterministic and an
//    included top-level statement never runs twice."
// This is the diamond shape the sentence names: main.sol includes left.sol and right.sol,
// and both include the same physical file leaf.sol. leaf.sol holds one executable
// statement, so "an included top-level statement never runs twice" fixes the output
// exactly: [L] once, never [L][L].
// The include inside left.sol is written "leaf.sol", relative to left.sol's own
// directory, and likewise in right.sol; both therefore name the same canonical file
// lib/leaf.sol. If either path named a different file the two includes would be distinct
// and the sentence would not apply at all, so the shared identity is the point of the
// fixture layout rather than an incidental detail.
// Executed entirely by included statements: main.sol contributes none, so the whole
// expected stdout is `[L]`. Uses print, so no platform line separator can enter the
// expected bytes.
include "lib/left.sol"
include "lib/right.sol"
