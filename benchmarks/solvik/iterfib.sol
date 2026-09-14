package bench

struct Main {

    pub func run(args: String...): Integer {
        var a: Long = 0
        var b: Long = 1
        var i: Long = 0
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
