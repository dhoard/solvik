package shortcircuit

class Main {

    public static risky(): Long {
        throw "must not run"
    }

    public static run(args: String...): Long {
        // && short-circuits: the right side must not evaluate when the
        // left side is false.
        let a: Bool = false && (Main.risky() > 0)
        System.out().println(a)
        // || short-circuits: the right side must not evaluate when the
        // left side is true.
        let b: Bool = true || (Main.risky() > 0)
        System.out().println(b)
        return 0
    }
}
