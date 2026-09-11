package trytail

// Regression: `try` as the final statement of a function. Every path that
// returns must compile cleanly; a path that can fall off the end of a
// value-returning function must be rejected (C130) rather than looping.

class Foo {

    public static both(x: Long): Long {
        try {
            if (x > 0) { return x }
            return -x
        } catch (e) {
            return 0
        }
    }

    public static swallow(x: Long): Long {
        try {
            if (x == 0) { return 999 }
        } catch (e) {
            return 0
        }
        return 1
    }

    public static rethrow(x: Long): Long {
        try {
            if (x < 0) { throw "negative" }
        } catch (e) {
            return 5
        }
        return 6
    }
}

class Main {

    public static run(args: String...): Long {
        stdout.println(Foo.both(7))
        stdout.println(Foo.both(-3))
        stdout.println(Foo.swallow(1))
        stdout.println(Foo.swallow(0))
        stdout.println(Foo.rethrow(-1))
        stdout.println(Foo.rethrow(2))
        return 0
    }
}
