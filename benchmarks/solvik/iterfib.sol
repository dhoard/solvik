package bench

struct Main {

    public func run(args: String...): Long {
        let mutable a: Long = 0
        let mutable b: Long = 1
        let mutable i: Long = 0
        while i < 9000000 {
            let t: Long = (a + b) % 1000000007
            a = b
            b = t
            i += 1
        }
        System.getOut().println(b)
        return 0
    }
}
