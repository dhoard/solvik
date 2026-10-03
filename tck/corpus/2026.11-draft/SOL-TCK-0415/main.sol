// Solvik TCK SOL-TCK-0415
// The instance call reaches the universal member and prints the class name `C`, not the static method's text.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Those names are reserved to protect the universal `Any` members, which are instance members reached through virtual dispatch; a static member never enters the dispatch table, so a `static func toString()` cannot replace `Any.toString()` any more than an instance method of another name can, and `instance.toString()` keeps reaching the universal member.
//   - for a class declaring `static val toString: Integer` and inheriting the default `Any.toString()`, the expression `C.toString` reads the static cell and `instance` formatting still calls `Any.toString()`.
//
class C {
    static func toString(): String {
        return "static"
    }

    C() {
    }
}
val c = C()
print("st" .. c.toString())
