// Solvik TCK SOL-TCK-0414
// The nested include in lib/m.sol resolves against lib/, so the helper in lib/n.sol is found.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every nested relative include is resolved against its including file, never the root directory.
//
include "lib/m.sol"
print("nm" .. m())
