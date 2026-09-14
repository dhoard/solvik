package invalid
struct Main {

    pub func run(args: String...): Integer {
        System.getOut().println(true .. false)
        return 0
    }
}
