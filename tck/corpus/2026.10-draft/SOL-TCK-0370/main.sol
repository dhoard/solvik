// Solvik TCK SOL-TCK-0370
// A path whose final name does not end in .sol pins SOLV-RESOL-007.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Require a non-empty path whose final file name ends in `.sol`; otherwise report `SOLV-RESOL-007` at the path literal.
//   - | `RESOL_INCLUDE_INVALID_PATH` | `SOLV-RESOL-007` | path literal |
//
include "foo.txt"
print("EXECUTED-INVALID")
