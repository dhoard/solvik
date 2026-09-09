package nullablepattern

enum E {
    Value
}

class Main {
    pub static run(args: String...): Int {
        value: E? = null
        return match value {
            E::Value => 1
            _ => 0
        }
    }
}
