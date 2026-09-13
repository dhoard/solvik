package shortcircuit

struct Main {

    public static func risky(): Long {
        throw Exception.new("must not run")
    }

    public static func run(args: String...): Long {
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
