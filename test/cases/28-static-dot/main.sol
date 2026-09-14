package dotcolon

struct Math2 {

    public func double(x: Long): Long {
        return x * 2
    }
}

struct Main {

    public func run(args: String...): Integer {
        // Dot-qualified calls select static methods on uppercase type names.
        let v: Long = Math2.double(4)
        System.getOut().println(v)
        return 0
    }
}
