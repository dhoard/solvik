package streams

class Main {

    public static run(args: String...): Long {
        stdout.println("out")
        stderr.println("err")
        stderr.redirect(stdout)
        stderr.println("redirected")
        return 0
    }
}
