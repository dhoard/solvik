package scope
class Main {
    public static run(args: String...): Long {
        let e: Long = 1
        try { throw "boom" } catch (e) {}
        stdout.println(e)
        return 0
    }
}
