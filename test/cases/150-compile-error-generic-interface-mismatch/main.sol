package genifmismatch

interface Source<T> {
    func get(self): T
}

struct StringSource implements Source<String> {
    public static func new(): Self {
        return Self {}
    }
    public func get(self): String {
        return "x"
    }
}

struct Main {
    public static func run(args: String...): Long {
        // StringSource conforms to Source<String>, not Source<Long>.
        let s: Source<Long> = StringSource.new()
        return 0
    }
}
