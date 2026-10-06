// Solvik TCK SOL-TCK-0373
// A qualified reference to a module no file declares pins SOLV-RESOL-015.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - | `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |
//
var x = nope::thing()
print("EXECUTED-INVALID")
