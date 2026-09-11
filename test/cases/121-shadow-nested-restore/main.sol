package shadow
class Main {
    public static run(args: String...): Long {
        let x: Long = 1
        if true {
            let x: Long = 2
            System.out().println(x)
        }
        System.out().println(x)
        return 0
    }
}
