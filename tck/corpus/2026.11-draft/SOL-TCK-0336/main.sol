// Solvik TCK SOL-TCK-0336
// A second class initializer block pins the specification-named SOLV-SEM-046.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A class declares **at most one** class initializer block. A second block is `SOLV-SEM-046`, reported on the later block.
//   - | `SEM_DUPLICATE_STATIC_BLOCK` | `SOLV-SEM-046` | a class declares more than one class initializer block |
//
class C {
    static {
        print("a")
    }

    static {
        print("b")
    }

    C() {
    }
}
val c = C()
print("EXECUTED-INVALID")
