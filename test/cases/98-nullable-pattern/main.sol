package nullablepattern

enum E {

    value
}

class Main {

    public static run(args: String...): Long {
        let value: E? = null
        return match value {
            E.value => 1
            _ => 0
        }
    }
}
