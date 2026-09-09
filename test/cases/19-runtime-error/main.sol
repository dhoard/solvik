package badruntime

class Main {
    pub static run(args: String...): Int {
        x: List<Int> = [1]
        stdout.println(x.get(5))
        return 0
    }
}
