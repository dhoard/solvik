package uncaught

struct Main {
    pub func run(args: String...): Integer {
        throw Exception.new("boom")
    }
}
