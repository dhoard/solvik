package badruntime

struct Main {

    pub func run(args: String...): Integer {
        let x: List<Long> = [1]
        System.getOut().println(x.get(5))
        return 0
    }
}
