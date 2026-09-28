// Solvik TCK SOL-TCK-0326
// A bare arithmetic expression in statement position is a value-producing non-call.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A call may be used as a statement. Other value-producing expressions cannot stand alone as statements.
//
1 + 1
print("EXECUTED-INVALID")
