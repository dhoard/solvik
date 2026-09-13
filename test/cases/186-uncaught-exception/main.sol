package uncaught

struct Main {
    public static func run(args: String...): Long {
        throw Exception.new("boom")
    }
}
