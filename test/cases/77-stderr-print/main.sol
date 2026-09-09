package regression
class Main {
pub static run(args: String...): Int {
stderr.print("a")
stderr.println("b")
stderr.redirect(stdout)
stderr.print("c")
stderr.println("d")
return 0
}
}
