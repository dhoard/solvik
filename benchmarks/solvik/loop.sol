package bench

struct Main {

    public func run(args: String...): Long {
        let mutable sum: Long = 0
        let mutable i: Long = 0
        while i < 50000000 {
            sum += i * 3 - 1
            i += 1
        }
        System.getOut().println(sum)
        return 0
    }
}
