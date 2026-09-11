package shadow
class Main {
    public static run(args: String...): Long {
        let m: Long = 1
        let mutable m: Long = 2
        m = 3
        stdout.println(m)
        return 0
    }
}
