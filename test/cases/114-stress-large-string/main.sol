package stressstr

struct Main {

    pub func run(args: String...): Integer {
        var s: String = ""
        var i: Long = 0
        while i < 50000 {
            s = s .. "abcdefgh"
            i += 1
        }
        System.getOut().println(s.length())
        System.getOut().println(s.startsWith("abc"))
        System.getOut().println(s.endsWith("h"))
        return 0
    }
}
