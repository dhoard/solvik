package regression

class Main {
    pub static run(args: String...): Int {
        x: List<Int> = [7]
        x.set(-1, 9)
        stdout.println(x.get(0))
        return 0
    }
}
