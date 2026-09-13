package shadowerr

struct Main {
    public static func run(args: String...): Long {
        let x: Long = 1
        { let x: Long = 2 }
        return x
    }
}
