// Solvik TCK SOL-TCK-0394
// With no matching case and no default, the statement switch does nothing and the program continues.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Requiring `default` makes value production explicit for `Integer`, `String`, and regex dispatch, while a statement `switch` may still omit `default` and do nothing when no label matches.
//
val x = 9
switch (x) {
    case 1:
        print("one")
}
print("swafter")
