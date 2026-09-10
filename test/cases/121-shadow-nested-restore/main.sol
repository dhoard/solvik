module shadow
class Main {
    public static run(args: String...): Long {
        let x: Long = 1
        if true {
            let x: Long = 2
            stdout.println(x)
        }
        stdout.println(x)
        return 0
    }
}
