package scope
struct Main {
    public func run(args: String...): Long {
        let n: Long = 1
        try { throw Exception.new("boom") } catch (e: Exception) {}
        System.getOut().println(n)
        return 0
    }
}
