package invalid
struct Main {

    pub func nothing() {}
    pub func run(args: String...): Integer {
        System.getOut().println("void=" .. Main.nothing())
        return 0
    }
}
