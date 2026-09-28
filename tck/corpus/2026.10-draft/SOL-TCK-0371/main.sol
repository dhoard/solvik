// Solvik TCK SOL-TCK-0371
// The empty path pins the non-empty half of the same rule.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Require a non-empty path whose final file name ends in `.sol`; otherwise report `SOLV-RESOL-007` at the path literal.
//   - | `RESOL_INCLUDE_INVALID_PATH` | `SOLV-RESOL-007` | path literal |
//
include ""
print("EXECUTED-INVALID")
