package enummissingpayload

enum E {

    value(Long)
}

struct Main {

    public func run(args: String...): Long {
        let value: E = E.value()
        return 0
    }
}
