package letimmutable

// `let` declares a runtime immutable binding: the binding cannot be reassigned
// after initialization. The referenced object may still be mutated.

struct Main {

    pub func run(args: String...): Integer {
        let value: Integer = 1
        value = 2
        return value
    }
}
