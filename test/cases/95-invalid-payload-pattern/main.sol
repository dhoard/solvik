package invalidpayloadpattern

enum E {

    value(Long)
    empty
}

struct Main {

    pub func run(args: String...): Integer {
        let value: E = E.value(1)
        return match value {
            E.value("wrong") => 1
            _ => 0
        }
    }
}
