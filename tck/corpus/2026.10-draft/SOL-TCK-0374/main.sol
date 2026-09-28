// Solvik TCK SOL-TCK-0374
// A value returned from a class initializer block pins SOLV-TYPE-011.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A class declares **at most one** class initializer block. A second block is `SOLV-SEM-046`, reported on the later block. The block holds statements, not declarations; a bare `return` exits it early, and a `return` with a value is `SOLV-TYPE-011` because the block returns nothing.
//
class C {
    static {
        return 1
    }

    C() {
    }
}
print("EXECUTED-INVALID")
