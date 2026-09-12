package uncaught

class Main {
    public static run(args: String...): Long {
        throw Exception.new("boom")
    }
}
