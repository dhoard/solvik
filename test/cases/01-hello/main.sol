package hello

struct Main {

    pub func run(args: String...): Integer {
        System.getOut().println("hello")
        System.getOut().print("world")
        System.getOut().println("!")
        return 0
    }
}
