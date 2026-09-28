// Solvik TCK SOL-TCK-0413
// print declares exactly one parameter, so a zero-argument call is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The predeclared `print`, `println`, and `exit` functions each declare exactly one parameter, so a call that supplies a different number of arguments is a compile-time error; built-ins participate in the ordinary resolved-callable model rather than receiving separate arity rules.
//
print()
print("EXECUTED-INVALID")
