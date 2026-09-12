package shortcircuit

class Main {

    public static risky(): Long {
        throw Exception.new("must not run")
    }

    public static run(args: String...): Long {
        // && short-circuits: the right side must not evaluate when the
        // left side is false.
        let a: Boolean = false && (Main.risky() > 0)
        System.out().println(a)
        // || short-circuits: the right side must not evaluate when the
        // left side is true.
        let b: Boolean = true || (Main.risky() > 0)
        System.out().println(b)
        return 0
    }
}
