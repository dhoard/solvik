package regression

struct Main {

    pub func run(args: String...): Integer {
        let x: List<Long> = [7]
        System.getOut().println(x.get(-1))
        return 0
    }
}
