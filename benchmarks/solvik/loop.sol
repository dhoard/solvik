package bench

struct Main {

    pub func run(args: String...): Integer {
        var sum: Long = 0
        var i: Long = 0
        while i < 50000000 {
            sum += i * 3 - 1
            i += 1
        }
        System.getOut().println(sum)
        return 0
    }
}
