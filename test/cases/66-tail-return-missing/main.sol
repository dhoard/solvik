package tailretmissing

// Regression: a value-returning function whose trailing `if` can fall off
// the end without returning used to compile into an infinite loop (jump to
// offset 0). It must be rejected with C130 instead.

class Foo {
    pub static maybe(x: Bool): Int {
        if (x) { return 1 }
    }
}

class Main {
    pub static run(args: String...): Int {
        stdout.println(Foo::maybe(false))
        return 0
    }
}
