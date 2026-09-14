package receiverlessstatic

struct X {
    pub func count(): Long { return 42 }
}

struct Main {
    pub func run(args: String...): Integer {
        System.getOut().println(X.count())
        return 0
    }
}
