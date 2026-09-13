package bench

struct Main {

    public func run(args: String...): Long {
        let list: List<Long> = List<Long>.new()
        let mutable i: Long = 0
        while i < 2000000 {
            list.add(i)
            i += 1
        }
        let mutable sum: Long = 0
        for v in list {
            sum += v
        }
        System.getOut().println(sum)
        return 0
    }
}
