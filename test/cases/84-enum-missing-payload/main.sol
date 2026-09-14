package enummissingpayload

enum E {

    value(Long)
}

struct Main {

    public func run(args: String...): Integer {
        let value: E = E.value()
        return 0
    }
}
