package variadiccall

struct Main {

    pub func count(values: Long...): Long {
        return values.size()
    }

    pub func run(args: String...): Integer {
        System.getOut().println(Main.count(1, 2, 3))
        return 0
    }
}
