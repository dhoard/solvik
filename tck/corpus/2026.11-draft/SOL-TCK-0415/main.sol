// Solvik TCK SOL-TCK-0415
// The instance call reaches the universal member and prints the class name `C`, not the static method's text.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - for a class declaring `var static toString: Integer` and inheriting the default `Any.toString()`, the expression `C.toString` reads the static cell and `instance` formatting still calls `Any.toString()`.
//
class C {
    method static toString(): String {
        return "static"
    }

    C() {
    }
}
var c: C = C()
print("st" .. c.toString())
