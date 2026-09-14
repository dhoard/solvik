package bench

struct Main {

    pub func run(args: String...): Integer {
        var count: Long = 0
        var i: Long = 0
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
