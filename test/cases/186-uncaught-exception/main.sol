package uncaught

struct Main {
    public func run(args: String...): Integer {
        throw Exception.new("boom")
    }
}
