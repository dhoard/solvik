package staticmethodinblock

struct Registry {
    static mutable total: Long = 0

    // A static block is part of the struct's static initialization and may
    // call receiver-less (static) methods of the same struct by bare name.
    static {
        total = tally()
    }

    // Receiver-less methods resolve to the struct by bare name inside a
    // static block (no struct or Self qualifier), and by `Struct.member`
    // elsewhere.
    public func tally(): Long { return 3 }
    public func snapshot(): Long { return Self.total }
}

struct Main {
    public func run(args: String...): Long {
        // Static dispatch: a receiver-less method is selected by the type.
        System.getOut().println(Registry.tally())
        System.getOut().println(Registry.snapshot())
        return 0
    }
}
