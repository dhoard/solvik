package tailretmissing

// Regression: a value-returning function whose trailing `if` can fall off
// the end without returning used to compile into an infinite loop (jump to
// offset 0). It must be rejected with C130 instead.

class Foo {

    public static maybe(x: Bool): Long {
        if (x) { return 1 }
    }
}

class Main {

    public static run(args: String...): Long {
        System.out().println(Foo.maybe(false))
        return 0
    }
}
