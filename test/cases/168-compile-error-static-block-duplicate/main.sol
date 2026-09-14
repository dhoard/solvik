package staticblocks

struct A {

    static { System.getOut().println(1) }
    static { System.getOut().println(2) }
}

struct Main {

    pub func run(args: String...): Integer {
        return 0
    }
}
