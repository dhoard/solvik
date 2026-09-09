package invalid
class Main {
    pub static nothing(): Void {}
    pub static run(args: String...): Int {
        stdout.println("void=" .. Main::nothing())
        return 0
    }
}
