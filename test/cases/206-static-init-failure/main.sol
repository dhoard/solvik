package staticinitfailure

// A failing static initializer is no longer a startup error: the class
// initializes lazily at its first active use, and that use fails. The
// program terminates with a runtime error naming the failure.

class A {

    static {
        throw Exception.new("init boom")
    }

    public static get(): Long {
        return 0
    }
}

class Main {

    public static run(args: String...): Long {
        // First active use of A: initialization fails here.
        return A.get()
    }
}
