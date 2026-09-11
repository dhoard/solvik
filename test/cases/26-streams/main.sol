package streams

class Main {

    public static run(args: String...): Long {
        System.out().println("out")
        System.err().println("err")
        System.err().redirect(System.out())
        System.err().println("redirected")
        return 0
    }
}
