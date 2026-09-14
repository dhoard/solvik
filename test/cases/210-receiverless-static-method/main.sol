package receiverlessstatic

struct X {
    public func count(): Long { return 42 }
}

struct Main {
    public func run(args: String...): Integer {
        System.getOut().println(X.count())
        return 0
    }
}
