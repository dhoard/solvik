package bench

struct Main {

    public func run(args: String...): Long {
        let mutable count: Long = 0
        let mutable i: Long = 0
        while i < 200000 {
            let re: Regex = Regex.new("[0-9]+")
            if re.matches("abc123def") {
                count += 1
            }
            i += 1
        }
        System.getOut().println(count)
        return 0
    }
}
