package stresscoll

struct Main {

    pub func run(args: String...): Integer {
        let xs: List<Long> = []
        var i: Long = 0
        while i < 100000 {
            xs.add(i)
            i += 1
        }
        var t: Long = 0
        var j: Integer = 0
        while j < xs.size() {
            t += xs.get(j)
            j += 1
        }
        System.getOut().println(xs.size())
        System.getOut().println(t)
        return 0
    }
}
