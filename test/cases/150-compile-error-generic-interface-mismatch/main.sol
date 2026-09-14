package genifmismatch

trait Source<T> {
    func get(self): T
}

struct StringSource implements Source<String> {
    public func new(): Self {
        return Self {}
    }
    public func get(self): String {
        return "x"
    }
}

struct Main {
    public func run(args: String...): Integer {
        // StringSource conforms to Source<String>, not Source<Long>.
        let s: Source<Long> = StringSource.new()
        return 0
    }
}
