package nullablepattern

enum E {

    value
}

struct Main {

    public func run(args: String...): Integer {
        let value: E? = null
        return match value {
            E.value => 1
            _ => 0
        }
    }
}
