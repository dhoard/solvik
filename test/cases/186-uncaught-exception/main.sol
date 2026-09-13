package uncaught

struct Main {
    public func run(args: String...): Long {
        throw Exception.new("boom")
    }
}
