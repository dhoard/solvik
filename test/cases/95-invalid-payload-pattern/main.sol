package invalidpayloadpattern

enum E {

    value(Long)
    empty
}

struct Main {

    public static func run(args: String...): Long {
        let value: E = E.value(1)
        return match value {
            E.value("wrong") => 1
            _ => 0
        }
    }
}
