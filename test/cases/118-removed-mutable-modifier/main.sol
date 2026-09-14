package removedmutable

// The `mutable` modifier was removed. Mutable bindings use `var` and mutable
// fields use `var` as well; `mutable` is no longer part of the language.

struct Main {

    pub func run(args: String...): Integer {
        mutable count: Long = 1
        return 0
    }
}
