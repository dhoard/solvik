// Solvik TCK SOL-TCK-0363
// A class extending the built-in Integer is the forbidden extension.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A built-in scalar cannot be extended and its `toString` cannot be overridden.
//
class MyInt extends Integer {
    MyInt() {
    }
}
print("EXECUTED-INVALID")
