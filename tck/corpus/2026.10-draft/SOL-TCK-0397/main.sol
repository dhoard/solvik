// Solvik TCK SOL-TCK-0397
// The $-containing path is literal and names no file, so the include is not found.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Shell interpolation, environment-variable expansion, URLs, classpath resources, package lookup, and non-file URI schemes are not part of the language.
//   - | `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |
//
include "$HOME/x.sol"
print("EXECUTED-INVALID")
