package bench

struct Main {

    pub func run(args: String...): Integer {
        var s: String = ""
        var i: Long = 0
        while i < 100000 {
            s = s .. i
            i += 1
        }
        System.getOut().println(s.length())
        return 0
    }
}
