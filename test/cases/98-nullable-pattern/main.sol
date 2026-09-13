package nullablepattern

enum E {

    value
}

struct Main {

    public static func run(args: String...): Long {
        let value: E? = null
        return match value {
            E.value => 1
            _ => 0
        }
    }
}
