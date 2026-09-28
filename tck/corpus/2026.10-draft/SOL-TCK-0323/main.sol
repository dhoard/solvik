// Solvik TCK SOL-TCK-0323
// An explicit main declaration is the exact forbidden shape.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The entry point is always implicit: declaring a function named `main` explicitly, in the root or in any included file, is a compile-time error.
//
func main() {
    print("main")
}
print("EXECUTED-INVALID")
