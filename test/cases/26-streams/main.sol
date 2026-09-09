package streams

class Main {
    pub static run(args: String...): Int {
        stdout.println("out")
        stderr.println("err")
        stderr.redirect(stdout)
        stderr.println("redirected")
        return 0
    }
}
