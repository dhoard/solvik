package regression

struct Main {

    pub func run(args: String...): Integer {
        System.getOut().println(Math.abs(-9223372036854775807 - 1))
        return 0
    }
}
