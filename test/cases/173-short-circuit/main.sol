package shortcircuit

struct Main {

    public func risky(): Long {
        throw Exception.new("must not run")
    }

    public func run(args: String...): Integer {
        // && short-circuits: the right side must not evaluate when the
        // left side is false.
        let a: Boolean = false && (Main.risky() > 0)
        System.getOut().println(a)
        // || short-circuits: the right side must not evaluate when the
        // left side is true.
        let b: Boolean = true || (Main.risky() > 0)
        System.getOut().println(b)
        return 0
    }
}
