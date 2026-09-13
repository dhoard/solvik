package bench

struct Main {

    public func run(args: String...): Long {
        let mutable s: String = ""
        let mutable i: Long = 0
        while i < 100000 {
            s = s .. i
            i += 1
        }
        System.getOut().println(s.length())
        return 0
    }
}
