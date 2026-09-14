package bench

struct Main {

    pub func run(args: String...): Integer {
        let list: List<Long> = List<Long>.new()
        var i: Long = 0
        while i < 2000000 {
            list.add(i)
            i += 1
        }
        var sum: Long = 0
        for v in list {
            sum += v
        }
        System.getOut().println(sum)
        return 0
    }
}
