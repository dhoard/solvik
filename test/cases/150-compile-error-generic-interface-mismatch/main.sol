package genifmismatch

interface Source<T> {
    get(): T
}

class StringSource implements Source<String> {
    public static new(): Self {
        return Self {}
    }
    public get(): String {
        return "x"
    }
}

class Main {
    public static run(args: String...): Long {
        // StringSource conforms to Source<String>, not Source<Long>.
        let s: Source<Long> = StringSource.new()
        return 0
    }
}
