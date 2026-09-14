package misplacedseparator

struct Main {

    pub func run(args: String...): Integer {
        // A digit separator may not touch a radix prefix.
        System.getOut().println(0x_ff)
        return 0
    }
}
