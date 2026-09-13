package trytail

// Regression: `try` as the final statement of a function. Every path that
// returns must compile cleanly; a path that can fall off the end of a
// value-returning function must be rejected (C130) rather than looping.

struct Foo {

    public static func both(x: Long): Long {
        try {
            if (x > 0) { return x }
            return -x
        } catch (e: Exception) {
            return 0
        }
    }

    public static func swallow(x: Long): Long {
        try {
            if (x == 0) { return 999 }
        } catch (e: Exception) {
            return 0
        }
        return 1
    }

    public static func rethrow(x: Long): Long {
        try {
            if (x < 0) { throw Exception.new("negative") }
        } catch (e: Exception) {
            return 5
        }
        return 6
    }
}

struct Main {

    public static func run(args: String...): Long {
        System.getOut().println(Foo.both(7))
        System.getOut().println(Foo.both(-3))
        System.getOut().println(Foo.swallow(1))
        System.getOut().println(Foo.swallow(0))
        System.getOut().println(Foo.rethrow(-1))
        System.getOut().println(Foo.rethrow(2))
        return 0
    }
}
