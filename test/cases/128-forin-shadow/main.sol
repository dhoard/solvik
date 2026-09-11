package scope
class Main {
    public static run(args: String...): Long {
        let x: Long = 1
        let l: List<Long> = [1, 2]
        for x in l {}
        stdout.println(x)
        return 0
    }
}
