package invalidpayloadpattern

enum E {
    Value(Int)
    Empty
}

class Main {
    pub static run(args: String...): Int {
        value: E = E::Value(1)
        return match value {
            E::Value("wrong") => 1
            _ => 0
        }
    }
}
