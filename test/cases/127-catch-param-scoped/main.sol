package scope
struct Main {
    pub func run(args: String...): Integer {
        let n: Long = 1
        try { throw Exception.new("boom") } catch (e: Exception) {}
        System.getOut().println(n)
        return 0
    }
}
