package scope
class Main {
    public static run(args: String...): Long {
        let n: Long = 1
        try { throw Exception.new("boom") } catch (e: Exception) {}
        System.getOut().println(n)
        return 0
    }
}
