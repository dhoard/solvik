package regression
class Main {

    public static run(args: String...): Long {
        System.err().print("a")
        System.err().println("b")
        System.err().redirect(System.out())
        System.err().print("c")
        System.err().println("d")
        return 0
    }
}
