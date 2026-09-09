module regression
class Main {

    public static run(args: String...): Long {
        stderr.print("a")
        stderr.println("b")
        stderr.redirect(stdout)
        stderr.print("c")
        stderr.println("d")
        return 0
    }
}
