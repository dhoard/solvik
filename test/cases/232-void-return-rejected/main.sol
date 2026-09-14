package voidreturnrejected

// A method that returns no value omits its return type.  `Void` is the
// internal result type for that case and cannot be written as a return type,
// so `: Void` is rejected with P003.

struct Main {

    public func run(args: String...): Integer {
        return 0
    }

    public func nothing(): Void {
    }
}
