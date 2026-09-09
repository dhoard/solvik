package enummissingpayload

enum E {
    Value(Int)
}

class Main {
    pub static run(args: String...): Int {
        value: E = E::Value()
        return 0
    }
}
