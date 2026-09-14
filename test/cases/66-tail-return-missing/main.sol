package tailretmissing

// Regression: a value-returning function whose trailing `if` can fall off
// the end without returning used to compile into an infinite loop (jump to
// offset 0). It must be rejected with C130 instead.

struct Foo {

    pub func maybe(x: Boolean): Long {
        if (x) { return 1 }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        System.getOut().println(Foo.maybe(false))
        return 0
    }
}
