// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "The written name is a single identifier: lowercase letters and digits with parts
//    joined by exactly one underscore, each part starting with a letter
//    (`[a-z][a-z0-9]*(_[a-z0-9]+)*`), and it is not a reserved word."
// `Bad_Name` is a single, lexically well-formed identifier that violates the naming rule
// (its first part starts with an uppercase letter), so the failure is a module-name
// violation rather than a lexical one, and the required-diagnostics registry names it:
//   "| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration or include directive |"
// so the manifest pins `SOLV-RESOL-012`.
// Deliberate scope limit: a name containing a dot (`module com.example.math`) is rejected
// with a parse error, because `.` terminates the declaration before any module-name check
// can run. The naming rule covers both shapes, but only the identifier-shaped violation
// can reach the check the registry row describes, so the dot shape is not asserted here --
// asserting a code for it would test which check happens to run first, not the rule.
// Sentinel per TCK.md section 10: the print would be observable if the bad name were
// accepted.
include "lib/m.sol"

print(1)
