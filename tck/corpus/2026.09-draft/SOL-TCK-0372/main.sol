// Solvik TCK SOL-TCK-0372
// The canonical target is a directory, so it is not a regular file and pins SOLV-RESOL-009.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - | `RESOL_INCLUDE_NOT_FILE` | `SOLV-RESOL-009` | include directive |
//   - The file is normalized and canonicalized before it is used as an identity.
//
include "lib/x.sol"
print("EXECUTED-INVALID")
