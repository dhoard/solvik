package enummissingpayload

enum E {

    value(Long)
}

struct Main {

    pub func run(args: String...): Integer {
        let value: E = E.value()
        return 0
    }
}
