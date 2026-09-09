package invalid
class Main {
    pub static run(args: String...): Int {
        stdout.println(true .. false)
        return 0
    }
}
