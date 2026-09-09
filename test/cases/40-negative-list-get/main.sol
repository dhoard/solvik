package regression

class Main {
    pub static run(args: String...): Int {
        x: List<Int> = [7]
        stdout.println(x.get(-1))
        return 0
    }
}
